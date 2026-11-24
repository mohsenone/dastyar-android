package app.dastyar.agent.tools

import android.Manifest
import android.app.AlarmManager
import android.app.usage.UsageStatsManager
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import app.dastyar.agent.data.ChatRepository
import app.dastyar.agent.data.MemoryRepository
import app.dastyar.agent.data.RoutineEntity
import app.dastyar.agent.data.RoutineRepository
import app.dastyar.agent.data.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class ToolRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ui: UiBridge,
    private val memory: MemoryRepository,
    private val routines: RoutineRepository,
    private val chats: ChatRepository,
    private val settings: SettingsStore,
    private val scheduler: RoutineSchedulerPort,
) : ToolExecutor {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val tools: List<Registered> = buildList {
        add(device())
        add(listNotifications())
        add(dismissNotification())
        add(readSms())
        add(sendSms())
        add(dial())
        add(placeCall())
        add(callLog())
        add(searchContacts())
        add(writeContact())
        add(listEvents())
        add(createEvent())
        add(deleteEvent())
        add(setAlarm())
        add(listFiles())
        add(readFile())
        add(writeFile())
        add(deleteFile())
        add(takePhoto())
        add(takeVideo())
        add(location())
        add(captureScreen())
        add(readScreen())
        add(a11yAction())
        add(webFetch())
        add(openUrl())
        add(openApp())
        add(shareText())
        add(clipboardRead())
        add(clipboardWrite())
        add(saveMemory())
        add(listMemories())
        add(deleteMemory())
        add(searchChats())
        add(createRoutine())
        add(shell())
        add(allFilesSettings())
    }

    override fun specs(): List<ToolSpec> = tools.map { it.spec }

    override fun riskOf(name: String): RiskLevel = tools.find { it.spec.name == name }?.risk ?: RiskLevel.DANGEROUS

    override fun summary(name: String, argsJson: String, lang: String): String {
        val tool = tools.find { it.spec.name == name } ?: return name
        return tool.confirm(jsonArgs(argsJson), lang)
    }

    override suspend fun execute(name: String, argsJson: String, ctx: ToolContext): ToolResult {
        if (AutomationGate.paused) {
            return ToolResult(false, bi(ctx.lang, "ابزارها متوقف‌اند.", "Tools are paused."))
        }
        val tool = tools.find { it.spec.name == name }
            ?: return ToolResult(false, "Unknown tool $name")
        return try {
            tool.run(jsonArgs(argsJson), ctx)
        } catch (error: SecurityException) {
            ToolResult(false, "Permission denied")
        } catch (error: Exception) {
            ToolResult(false, (error.message ?: error.javaClass.simpleName).take(400))
        }
    }

    private fun device() = tool(
        name = "device_context",
        description = "Read battery, connectivity, time, locale, and launcher apps. Does not change the phone.",
        schema = obj(),
        risk = RiskLevel.SAFE,
        confirm = { _, lang -> bi(lang, "خواندن وضعیت دستگاه", "Read device context") },
    ) { _, _ ->
        val battery = context.getSystemService(android.os.BatteryManager::class.java)
            .getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val net = when {
            caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
            caps == null -> "offline"
            else -> "other"
        }
        val apps = launcherApps().take(40).joinToString("\n") { "${it.first} (${it.second})" }
        val foreground = foregroundApp()
        ToolResult(
            true,
            "model=${Build.MODEL}; sdk=${Build.VERSION.SDK_INT}; battery=$battery%; network=$net; " +
                "locale=${java.util.Locale.getDefault()}; time=${System.currentTimeMillis()}; foreground=$foreground\n$apps",
        )
    }

    private fun listNotifications() = tool(
        name = "list_notifications",
        description = "List active notifications. One-time codes and banking text are redacted.",
        schema = obj(),
        risk = RiskLevel.SAFE,
        confirm = { _, lang -> bi(lang, "خواندن اعلان‌ها", "Read notifications") },
    ) { _, ctx ->
        if (!listenerEnabled()) {
            return@tool ToolResult(false, bi(ctx.lang, "دسترسی اعلان داده نشده.", "Notification access is off."))
        }
        val rows = DastyarNotificationListener.instance?.snapshot().orEmpty()
        if (rows.isEmpty()) return@tool ToolResult(true, bi(ctx.lang, "اعلانی نیست.", "No active notifications."))
        ToolResult(true, rows.joinToString("\n") { "${it.key} :: ${it.summary}" })
    }

    private fun dismissNotification() = tool(
        name = "dismiss_notification",
        description = "Dismiss one notification by the key returned from list_notifications.",
        schema = obj("key" to str()),
        risk = RiskLevel.CONFIRM,
        confirm = { args, lang -> bi(lang, "بستن اعلان ${args.optString("key")}", "Dismiss notification ${args.optString("key")}") },
    ) { args, ctx ->
        val key = args.optString("key")
        if (key.isBlank()) return@tool ToolResult(false, "Missing key")
        val ok = DastyarNotificationListener.instance?.dismiss(key) == true
        ToolResult(ok, if (ok) bi(ctx.lang, "بسته شد.", "Dismissed.") else "Listener unavailable")
    }

    private fun readSms() = tool(
        name = "read_sms",
        description = "Read recent SMS inbox messages. Requires permission. Codes may be redacted.",
        schema = obj("limit" to int()),
        risk = RiskLevel.CONFIRM,
        confirm = { _, lang -> bi(lang, "خواندن پیامک‌های اخیر", "Read recent SMS messages") },
    ) { args, ctx ->
        val granted = ui.ensurePermissions(
            listOf(Manifest.permission.READ_SMS),
            bi(ctx.lang, "برای خواندن صندوق پیامک به اجازه نیاز است.", "Dastyar needs permission to read SMS."),
        )
        if (!granted) return@tool ToolResult(false, "Permission denied")
        val limit = args.optInt("limit", 15).coerceIn(1, 30)
        val rows = mutableListOf<String>()
        context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            null,
            null,
            "date DESC",
        )?.use { cursor ->
            val address = cursor.getColumnIndex("address")
            val body = cursor.getColumnIndex("body")
            while (cursor.moveToNext() && rows.size < limit) {
                val text = Redaction.notification("sms", cursor.getString(address).orEmpty(), cursor.getString(body).orEmpty())
                rows += text
            }
        }
        ToolResult(true, rows.joinToString("\n").ifBlank { "Inbox empty" })
    }

    private fun sendSms() = tool(
        name = "send_sms",
        description = "Send an SMS. Always confirm the recipient and the full body with the user first.",
        schema = obj("to" to str(), "body" to str()),
        risk = RiskLevel.DANGEROUS,
        confirm = { args, lang ->
            bi(
                lang,
                "ارسال پیامک به ${args.optString("to")}:\n${args.optString("body")}",
                "Send SMS to ${args.optString("to")}:\n${args.optString("body")}",
            )
        },
    ) { args, ctx ->
        val to = args.optString("to").trim()
        val body = args.optString("body")
        if (to.isBlank() || body.isBlank()) return@tool ToolResult(false, "Recipient and body are required")
        val granted = ui.ensurePermissions(
            listOf(Manifest.permission.SEND_SMS),
            bi(ctx.lang, "برای ارسال پیامک به اجازه نیاز است.", "Dastyar needs permission to send SMS."),
        )
        if (!granted) return@tool ToolResult(false, "Permission denied")
        val sms = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = sms.divideMessage(body)
        if (parts.size <= 1) sms.sendTextMessage(to, null, body, null, null)
        else sms.sendMultipartTextMessage(to, null, parts, null, null)
        ToolResult(true, "SMS handed to the system for $to")
    }

    private fun dial() = tool(
        name = "dial_phone",
        description = "Open the phone dialer with a number. The user still has to press call.",
        schema = obj("number" to str()),
        risk = RiskLevel.CONFIRM,
        confirm = { args, lang -> bi(lang, "باز کردن شماره‌گیر برای ${args.optString("number")}", "Open the dialer for ${args.optString("number")}") },
    ) { args, _ ->
        val number = args.optString("number").trim()
        if (number.isBlank()) return@tool ToolResult(false, "Missing number")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        ToolResult(true, "Dialer opened")
    }

    private fun placeCall() = tool(
        name = "place_call",
        description = "Place a phone call immediately with ACTION_CALL. Dangerous.",
        schema = obj("number" to str()),
        risk = RiskLevel.DANGEROUS,
        confirm = { args, lang -> bi(lang, "تماس فوری با ${args.optString("number")}", "Place a call to ${args.optString("number")}") },
    ) { args, ctx ->
        val number = args.optString("number").trim()
        if (number.isBlank()) return@tool ToolResult(false, "Missing number")
        val granted = ui.ensurePermissions(
            listOf(Manifest.permission.CALL_PHONE),
            bi(ctx.lang, "برای تماس مستقیم به اجازه نیاز است.", "Dastyar needs permission to place a call."),
        )
        if (!granted) return@tool ToolResult(false, "Permission denied")
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        ToolResult(true, "Call started")
    }

    private fun callLog() = tool(
        name = "read_call_log",
        description = "Read recent call log entries.",
        schema = obj("limit" to int()),
        risk = RiskLevel.CONFIRM,
        confirm = { _, lang -> bi(lang, "خواندن گزارش تماس", "Read the call log") },
    ) { args, ctx ->
        val granted = ui.ensurePermissions(
            listOf(Manifest.permission.READ_CALL_LOG),
            bi(ctx.lang, "برای خواندن گزارش تماس به اجازه نیاز است.", "Dastyar needs permission to read the call log."),
        )
        if (!granted) return@tool ToolResult(false, "Permission denied")
        val limit = args.optInt("limit", 15).coerceIn(1, 30)
        val rows = mutableListOf<String>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
            null,
            null,
            "${CallLog.Calls.DATE} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext() && rows.size < limit) {
                rows += "${cursor.getString(0)} type=${cursor.getInt(1)} at=${cursor.getLong(2)} dur=${cursor.getLong(3)}"
            }
        }
        ToolResult(true, rows.joinToString("\n").ifBlank { "Call log empty" })
    }

    private fun searchContacts() = tool(
        name = "search_contacts",
        description = "Search contacts by name.",
        schema = obj("query" to str()),
        risk = RiskLevel.SAFE,
        confirm = { args, lang -> bi(lang, "جستجوی مخاطب ${args.optString("query")}", "Search contacts for ${args.optString("query")}") },
    ) { args, ctx ->
        val granted = ui.ensurePermissions(
            listOf(Manifest.permission.READ_CONTACTS),
            bi(ctx.lang, "برای جستجوی مخاطبین به اجازه نیاز است.", "Dastyar needs permission to read contacts."),
        )
        if (!granted) return@tool ToolResult(false, "Permission denied")
        val query = args.optString("query").trim()
        val uri = if (query.isBlank()) {
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        } else {
            Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query))
        }
        val rows = mutableListOf<String>()
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext() && rows.size < 20) {
                rows += "${cursor.getString(0)} — ${cursor.getString(1)}"
            }
        }
        ToolResult(true, rows.joinToString("\n").ifBlank { "No contacts" })
    }

    private fun writeContact() = tool(
        name = "write_contact",
        description = "Create a contact with a name and phone number.",
        schema = obj("name" to str(), "phone" to str()),
        risk = RiskLevel.CONFIRM,
        confirm = { args, lang ->
            bi(lang, "افزودن مخاطب ${args.optString("name")} / ${args.optString("phone")}", "Add contact ${args.optString("name")} / ${args.optString("phone")}")
        },
    ) { args, ctx ->
        val granted = ui.ensurePermissions(
            listOf(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS),
            bi(ctx.lang, "برای نوشتن مخاطب به اجازه نیاز است.", "Dastyar needs permission to write a contact."),
        )
        if (!granted) return@tool ToolResult(false, "Permission denied")
        val name = args.optString("name").trim()
        val phone = args.optString("phone").trim()
        if (name.isBlank()) return@tool ToolResult(false, "Missing name")
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build()
        ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            .build()
        if (phone.isNotBlank()) {
            ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build()
        }
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        ToolResult(true, "Contact saved")
    }

    private fun listEvents() = tool(
        name = "list_events",
        description = "List calendar events in the upcoming days.",
        schema = obj("days" to int()),
        risk = RiskLevel.SAFE,
        confirm = { _, lang -> bi(lang, "خواندن تقویم", "Read the calendar") },
    ) { args, ctx ->
        val granted = ui.ensurePermissions(
            listOf(Manifest.permission.READ_CALENDAR),
            bi(ctx.lang, "برای خواندن تقویم به اجازه نیاز است.", "Dastyar needs permission to read the calendar."),
        )
        if (!granted) return@tool ToolResult(false, "Permission denied")
        val days = args.optInt("days", 14).coerceIn(1, 60)
        val start = System.currentTimeMillis()
        val end = start + days * 24L * 3600_000L
        val rows = mutableListOf<String>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART),
            "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
            arrayOf(start.toString(), end.toString()),
            "${CalendarContract.Events.DTSTART} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && rows.size < 30) {
                rows += "#${cursor.getLong(0)} ${cursor.getString(1)} @ ${cursor.getLong(2)}"
            }
        }
        ToolResult(true, rows.joinToString("\n").ifBlank { "No upcoming events" })
    }

    private fun createEvent() = tool(
        name = "create_event",
        description = "Create a calendar event. Confirm title and time.",
        schema = obj("title" to str(), "startMillis" to int(), "endMillis" to int(), "description" to str()),
        risk = RiskLevel.CONFIRM,
        confirm = { args, lang ->
            bi(
                lang,
                "ساخت رویداد «${args.optString("title")}» در ${args.optLong("startMillis")}",
                "Create event \"${args.optString("title")}\" at ${args.optLong("startMillis")}",
            )
        },
    ) { args, ctx ->
        val granted = ui.ensurePermissions(
            listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            bi(ctx.lang, "برای نوشتن تقویم به اجازه نیاز است.", "Dastyar needs permission to write the calendar."),
        )
        if (!granted) return@tool ToolResult(false, "Permission denied")
        val title = args.optString("title").trim()
        val start = args.optLong("startMillis")
        if (title.isBlank() || start <= 0L) return@tool ToolResult(false, "Title and startMillis are required")
        val end = args.optLong("endMillis").takeIf { it > start } ?: (start + 3600_000L)
        val calendarId = findCalendarId() ?: return@tool ToolResult(false, "No writable calendar")
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, args.optString("description"))
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        ToolResult(uri != null, if (uri != null) "Event ${ContentUris.parseId(uri)} created" else "Insert failed")
    }

    private fun deleteEvent() = tool(
        name = "delete_event",
        description = "Delete a calendar event by id.",
        schema = obj("eventId" to int()),
        risk = RiskLevel.DANGEROUS,
        confirm = { args, lang -> bi(lang, "حذف رویداد ${args.optLong("eventId")}", "Delete event ${args.optLong("eventId")}") },
    ) { args, ctx ->
        val granted = ui.ensurePermissions(
            listOf(Manifest.permission.WRITE_CALENDAR),
            bi(ctx.lang, "برای حذف رویداد به اجازه نیاز است.", "Dastyar needs permission to delete a calendar event."),
        )
        if (!granted) return@tool ToolResult(false, "Permission denied")
        val id = args.optLong("eventId")
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        val rows = context.contentResolver.delete(uri, null, null)
        ToolResult(rows > 0, if (rows > 0) "Deleted" else "Event not found")
    }

    private fun setAlarm() = tool(
        name = "set_alarm",
        description = "Set an alarm in the clock app.",
        schema = obj("hour" to int(), "minute" to int(), "message" to str()),
        risk = RiskLevel.CONFIRM,
        confirm = { args, lang ->
            bi(lang, "تنظیم زنگ ${args.optInt("hour")}:${args.optInt("minute")} ${args.optString("message")}", "Set alarm ${args.optInt("hour")}:${args.optInt("minute")} ${args.optString("message")}")
        },
    ) { args, _ ->
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, args.optInt("hour"))
            .putExtra(AlarmClock.EXTRA_MINUTES, args.optInt("minute").coerceIn(0, 59))
            .putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("message"))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        ToolResult(true, "Alarm intent sent")
    }

    private fun listFiles() = tool(
        name = "list_files",
        description = "List files in the user-picked folder or the app private directory.",
        schema = obj("path" to str()),
        risk = RiskLevel.SAFE,
        confirm = { _, lang -> bi(lang, "فهرست فایل‌ها", "List files") },
    ) { args, ctx ->
        val dir = resolveDir(args.optString("path"), ctx) ?: return@tool ToolResult(false, "No folder is authorized yet")
        val names = dir.list()?.take(80)?.joinToString("\n").orEmpty()
        ToolResult(true, names.ifBlank { "(empty)" })
    }

    private fun readFile() = tool(
        name = "read_file",
        description = "Read a text file the user has authorized.",
        schema = obj("path" to str()),
        risk = RiskLevel.CONFIRM,
        confirm = { args, lang -> bi(lang, "خواندن ${args.optString("path")}", "Read ${args.optString("path")}") },
    ) { args, ctx ->
        val file = resolveFile(args.optString("path"), ctx) ?: return@tool ToolResult(false, "File is outside the authorized folders")
        if (!file.exists() || !file.isFile) return@tool ToolResult(false, "File not found")
        val text = file.readText().take(20_000)
        ToolResult(true, text)
    }

    private fun writeFile() = tool(
        name = "write_file",
        description = "Write or overwrite a text file in an authorized folder.",
        schema = obj("path" to str(), "content" to str()),
        risk = RiskLevel.DANGEROUS,
        confirm = { args, lang -> bi(lang, "نوشتن روی ${args.optString("path")}", "Write ${args.optString("path")}") },
    ) { args, ctx ->
        val file = resolveFile(args.optString("path"), ctx, createParent = true)
            ?: return@tool ToolResult(false, "Path is not authorized")
        file.parentFile?.mkdirs()
        file.writeText(args.optString("content"))
        ToolResult(true, "Wrote ${file.name}")
    }

    private fun deleteFile() = tool(
        name = "delete_file",
        description = "Delete a file in an authorized folder.",
        schema = obj("path" to str()),
        risk = RiskLevel.DANGEROUS,
        confirm = { args, lang -> bi(lang, "حذف ${args.optString("path")}", "Delete ${args.optString("path")}") },
    ) { args, ctx ->
        val file = resolveFile(args.optString("path"), ctx) ?: return@tool ToolResult(false, "Path is not authorized")
        val ok = file.delete()
        ToolResult(ok, if (ok) "Deleted" else "Could not delete")
    }

    private fun takePhoto() = tool(
        name = "take_photo",
        description = "Take a photo, show a preview, and only then attach it for the model.",
        schema = obj(),
        risk = RiskLevel.CONFIRM,
        confirm = { _, lang -> bi(lang, "گرفتن عکس. پیش‌نمایش را قبل از ارسال می‌بینید.", "Take a photo. You will preview it before it is sent.") },
    ) { _, ctx ->
        val granted = ui.ensurePermissions(
            listOf(Manifest.permission.CAMERA),
            bi(ctx.lang, "برای دوربین به اجازه نیاز است.", "Dastyar needs the camera."),
        )
        if (!granted) return@tool ToolResult(false, "Permission denied")
        val path = ui.takePhoto() ?: return@tool ToolResult(false, "Camera cancelled")
        val send = ui.confirmImage(
            path,
            bi(ctx.lang, "ارسال این عکس به مدل؟", "Send this photo to the model?"),
            bi(ctx.lang, "عکس فقط به ارائه‌دهندهٔ انتخاب‌شده فرستاده می‌شود.", "The photo goes only to the provider you selected."),
        )
        if (!send) {
            File(path).delete()
            return@tool ToolResult(false, "User did not send the photo")
        }
        ToolResult(true, "Photo attached", listOf(path))
    }

    private fun takeVideo() = tool(
        name = "take_video",
        description = "Record a short video with the system camera. The file stays on device unless the user shares it.",
        schema = obj(),
        risk = RiskLevel.CONFIRM,
        confirm = { _, lang -> bi(lang, "ضبط ویدیو با دوربین سیستم", "Record a video with the system camera") },
    ) { _, ctx ->
        val granted = ui.ensurePermissions(
            listOf(Manifest.permission.CAMERA),
            bi(ctx.lang, "برای دوربین به اجازه نیاز است.", "Dastyar needs the camera."),
        )
        if (!granted) return@tool ToolResult(false, "Permission denied")
        val path = ui.captureVideo() ?: return@tool ToolResult(false, "Video cancelled")
        ToolResult(true, "Video saved at $path")
    }

    private fun location() = tool(
        name = "get_location",
        description = "Read the current or last location. background=true asks for background location separately.",
        schema = obj("background" to bool()),
        risk = RiskLevel.CONFIRM,
        confirm = { args, lang ->
            if (args.optBoolean("background")) {
                bi(lang, "موقعیت پس‌زمینه. این دسترسی جداگانه است.", "Background location. This is a separate grant.")
            } else {
                bi(lang, "خواندن موقعیت فعلی", "Read the current location")
            }
        },
    ) { args, ctx ->
        val granted = ui.ensurePermissions(
            listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            bi(ctx.lang, "برای موقعیت به اجازه نیاز است.", "Dastyar needs location permission."),
        )
        if (!granted) return@tool ToolResult(false, "Permission denied")
        if (args.optBoolean("background")) {
            val bg = if (Build.VERSION.SDK_INT >= 29) {
                ui.ensurePermissions(
                    listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    bi(ctx.lang, "موقعیت پس‌زمینه فقط اگر یک روال واقعاً لازم دارد. می‌توانید رد کنید.", "Background location is only for a routine that truly needs it. You can deny it."),
                )
            } else {
                true
            }
            if (!bg) return@tool ToolResult(false, "Background location was not granted")
        }
        val fix = withTimeoutOrNull(15_000) { currentLocation() } ?: "Location timed out"
        ToolResult(true, fix)
    }

    private fun captureScreen() = tool(
        name = "capture_screen",
        description = "Capture one screenshot with the system MediaProjection consent dialog, then ask before sending it to the model.",
        schema = obj(),
        risk = RiskLevel.CONFIRM,
        confirm = { _, lang -> bi(lang, "گرفتن نماگرفت. سامانه اجازه می‌پرسد.", "Capture the screen. Android will ask for consent.") },
    ) { _, ctx ->
        if (!ui.isResumed()) return@tool ToolResult(false, "Open Dastyar to capture the screen")
        val path = ui.captureScreen() ?: return@tool ToolResult(false, "Screen capture was not granted")
        val send = ui.confirmImage(
            path,
            bi(ctx.lang, "این نماگرفت برای مدل فرستاده شود؟", "Send this screenshot to the model?"),
            bi(ctx.lang, "تصویر فقط به ارائه‌دهندهٔ انتخاب‌شده می‌رود.", "The image goes only to the provider you selected."),
        )
        if (!send) {
            File(path).delete()
            return@tool ToolResult(false, "User did not send the screenshot")
        }
        ToolResult(true, "Screenshot attached", listOf(path))
    }

    private fun readScreen() = tool(
        name = "read_screen",
        description = "Read visible text from the current window using the accessibility service.",
        schema = obj(),
        risk = RiskLevel.CONFIRM,
        confirm = { _, lang -> bi(lang, "خواندن متن صفحه. ممکن است اطلاعات حساس باشد.", "Read on-screen text. It may include sensitive content.") },
    ) { _, ctx ->
        val service = DastyarAccessibilityService.instance
            ?: return@tool ToolResult(false, bi(ctx.lang, "دسترس‌پذیری خاموش است. از تنظیمات دستیار و صفحهٔ توضیح آن را روشن کنید.", "Accessibility is off. Enable it from Dastyar settings after the disclosure."))
        val text = service.dumpText()
        ToolResult(true, text.ifBlank { "No text on screen" })
    }

    private fun a11yAction() = tool(
        name = "accessibility_action",
        description = "Perform confirmed taps, text entry, or scrolls in another app. Pass a steps array.",
        schema = """{"type":"object","properties":{"summary":{"type":"string"},"steps":{"type":"array","items":{"type":"object"}}},"required":["steps"]}""",
        risk = RiskLevel.DANGEROUS,
        confirm = { args, lang ->
            val summary = args.optString("summary")
            val steps = args.optJSONArray("steps")
            val lines = StringBuilder(summary.ifBlank { bi(lang, "کنترل برنامهٔ دیگر", "Control another app") })
            if (steps != null) {
                for (i in 0 until steps.length()) {
                    val step = steps.optJSONObject(i)
                    lines.append("\n").append(i + 1).append(". ").append(step?.optString("type")).append(" ")
                        .append(step?.optString("text")).append(" ").append(step?.optString("value"))
                }
            }
            lines.toString()
        },
    ) { args, ctx ->
        val service = DastyarAccessibilityService.instance
            ?: return@tool ToolResult(false, bi(ctx.lang, "دسترس‌پذیری خاموش است.", "Accessibility is off."))
        val steps = args.optJSONArray("steps") ?: return@tool ToolResult(false, "Missing steps")
        ToolResult(true, service.performSteps(steps))
    }

    private fun webFetch() = tool(
        name = "web_fetch",
        description = "HTTP request from the phone. GET and POST both show the URL for confirmation. Non-GET is dangerous.",
        schema = obj("url" to str(), "method" to str(), "body" to str()),
        risk = RiskLevel.CONFIRM,
        confirm = { args, lang ->
            val method = args.optString("method", "GET").uppercase()
            bi(lang, "$method ${args.optString("url")}", "$method ${args.optString("url")}")
        },
    ) { args, ctx ->
        val method = args.optString("method", "GET").uppercase()
        if (method != "GET") {
            // The agent loop confirms once. Non-GET is marked dangerous via risk override below.
        }
        val url = args.optString("url").trim()
        val parsed = runCatching { Uri.parse(url) }.getOrNull()
        if (parsed == null || parsed.scheme !in setOf("https", "http")) {
            return@tool ToolResult(false, "Only http and https URLs are allowed")
        }
        val host = parsed.host.orEmpty()
        val allow = settings.current().webAllowlist.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (allow.isNotEmpty() && allow.none { host.equals(it, true) || host.endsWith(".$it", true) }) {
            return@tool ToolResult(false, "Host is not on the allowlist")
        }
        val builder = Request.Builder().url(url)
        val request = if (method == "GET") {
            builder.get().build()
        } else {
            builder.method(method, args.optString("body").toRequestBody("application/json".toMediaType())).build()
        }
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().take(100_000)
            ToolResult(response.isSuccessful, "HTTP ${response.code}\n$text")
        }
    }

    private fun openUrl() = tool(
        name = "open_url",
        description = "Open an http(s) URL in the browser. The URL is shown in the chat chip.",
        schema = obj("url" to str()),
        risk = RiskLevel.SAFE,
        confirm = { args, _ -> args.optString("url") },
    ) { args, _ ->
        val url = args.optString("url").trim()
        val parsed = Uri.parse(url)
        if (parsed.scheme !in setOf("https", "http")) return@tool ToolResult(false, "Only http(s)")
        context.startActivity(Intent(Intent.ACTION_VIEW, parsed).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ToolResult(true, "Opened $url")
    }

    private fun openApp() = tool(
        name = "open_app",
        description = "Open an installed app by package name.",
        schema = obj("package" to str()),
        risk = RiskLevel.SAFE,
        confirm = { args, _ -> args.optString("package") },
    ) { args, _ ->
        val pkg = args.optString("package").trim()
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return@tool ToolResult(false, "App is not installed")
        context.startActivity(intent)
        ToolResult(true, "Opened $pkg")
    }

    private fun shareText() = tool(
        name = "share_text",
        description = "Open the system share sheet with text.",
        schema = obj("text" to str()),
        risk = RiskLevel.CONFIRM,
        confirm = { args, lang -> bi(lang, "هم‌رسانی:\n${args.optString("text").take(280)}", "Share:\n${args.optString("text").take(280)}") },
    ) { args, _ ->
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, args.optString("text"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(send, "Dastyar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ToolResult(true, "Share sheet opened")
    }

    private fun clipboardRead() = tool(
        name = "clipboard_read",
        description = "Read the clipboard. Android may block this unless Dastyar is in the foreground.",
        schema = obj(),
        risk = RiskLevel.CONFIRM,
        confirm = { _, lang -> bi(lang, "خواندن کلیپ‌بورد", "Read the clipboard") },
    ) { _, ctx ->
        val clip = context.getSystemService(android.content.ClipboardManager::class.java).primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isBlank()) {
            ToolResult(false, bi(ctx.lang, "کلیپ‌بورد خالی است یا سیستم اجازه نداد.", "Clipboard is empty or the system blocked the read."))
        } else {
            ToolResult(true, text.take(4000))
        }
    }

    private fun clipboardWrite() = tool(
        name = "clipboard_write",
        description = "Copy text to the clipboard.",
        schema = obj("text" to str()),
        risk = RiskLevel.CONFIRM,
        confirm = { args, lang -> bi(lang, "نوشتن در کلیپ‌بورد: ${args.optString("text").take(180)}", "Copy to clipboard: ${args.optString("text").take(180)}") },
    ) { args, _ ->
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("dastyar", args.optString("text")))
        ToolResult(true, "Copied")
    }

    private fun saveMemory() = tool(
        name = "save_memory",
        description = "Save a short fact for later chats.",
        schema = obj("fact" to str()),
        risk = RiskLevel.SAFE,
        confirm = { args, _ -> args.optString("fact") },
    ) { args, _ ->
        val fact = args.optString("fact").trim()
        if (fact.isBlank()) return@tool ToolResult(false, "Empty fact")
        val id = memory.add(fact.take(500))
        ToolResult(true, "Saved $id")
    }

    private fun listMemories() = tool(
        name = "list_memories",
        description = "List saved facts.",
        schema = obj(),
        risk = RiskLevel.SAFE,
        confirm = { _, _ -> "memories" },
    ) { _, _ ->
        val rows = memory.latest(30)
        ToolResult(true, rows.joinToString("\n") { "${it.id}: ${it.fact}" }.ifBlank { "No memories" })
    }

    private fun deleteMemory() = tool(
        name = "delete_memory",
        description = "Delete one saved fact by id.",
        schema = obj("id" to str()),
        risk = RiskLevel.CONFIRM,
        confirm = { args, lang -> bi(lang, "حذف یادداشت ${args.optString("id")}", "Delete memory ${args.optString("id")}") },
    ) { args, _ ->
        memory.delete(args.optString("id"))
        ToolResult(true, "Deleted")
    }

    private fun searchChats() = tool(
        name = "search_chats",
        description = "Search local chat titles and messages.",
        schema = obj("query" to str()),
        risk = RiskLevel.SAFE,
        confirm = { args, _ -> args.optString("query") },
    ) { args, _ ->
        val rows = chats.search(args.optString("query")).first().take(20)
        ToolResult(true, rows.joinToString("\n") { "${it.id} ${it.title}" }.ifBlank { "No chats" })
    }

    private fun createRoutine() = tool(
        name = "create_routine",
        description = "Schedule a daily or one-shot prompt. Unattended mode defaults off and must be explicit.",
        schema = obj(
            "title" to str(),
            "prompt" to str(),
            "hour" to int(),
            "minute" to int(),
            "repeatDaily" to bool(),
            "allowUnattended" to bool(),
        ),
        risk = RiskLevel.CONFIRM,
        confirm = { args, lang ->
            val unattended = args.optBoolean("allowUnattended")
            val warn = if (unattended) {
                bi(lang, "\nهشدار: این روال بدون پرسش عمل می‌کند.", "\nWarning: this routine would act without asking.")
            } else {
                ""
            }
            bi(
                lang,
                "روال «${args.optString("title")}» ساعت ${args.optInt("hour")}:${args.optInt("minute")}\n${args.optString("prompt")}$warn",
                "Routine \"${args.optString("title")}\" at ${args.optInt("hour")}:${args.optInt("minute")}\n${args.optString("prompt")}$warn",
            )
        },
    ) { args, _ ->
        val id = UUID.randomUUID().toString()
        routines.save(
            RoutineEntity(
                id = id,
                title = args.optString("title").ifBlank { "Routine" },
                prompt = args.optString("prompt"),
                hour = args.optInt("hour").coerceIn(0, 23),
                minute = args.optInt("minute").coerceIn(0, 59),
                repeatDaily = args.optBoolean("repeatDaily", true),
                enabled = true,
                allowUnattended = args.optBoolean("allowUnattended", false),
                createdAt = System.currentTimeMillis(),
            ),
        )
        scheduler.schedule(id)
        ToolResult(true, "Routine $id scheduled")
    }

    private fun shell() = tool(
        name = "sandbox_shell",
        description = "Run a command as the app user with the working directory set to the app private files folder. No root.",
        schema = obj("command" to str()),
        risk = RiskLevel.DANGEROUS,
        confirm = { args, lang -> bi(lang, "اجرای فرمان:\n${args.optString("command")}", "Run command:\n${args.optString("command")}") },
    ) { args, _ ->
        val command = args.optString("command")
        if (command.isBlank()) return@tool ToolResult(false, "Empty command")
        if (Regex("(?i)(^|[;&|`\\n])\\s*(su|sudo)\\b").containsMatchIn(command)) {
            return@tool ToolResult(false, "su is not available")
        }
        val result = withContext(Dispatchers.IO) {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(context.filesDir)
                .redirectErrorStream(false)
                .apply {
                    environment()["HOME"] = context.filesDir.absolutePath
                    environment()["TMPDIR"] = context.cacheDir.absolutePath
                }
                .start()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext "Timed out"
            }
            val out = process.inputStream.bufferedReader().readText().take(16_000)
            val err = process.errorStream.bufferedReader().readText().take(4_000)
            "exit=${process.exitValue()}\n$out\n$err"
        }
        ToolResult(true, result)
    }

    private fun allFilesSettings() = tool(
        name = "open_all_files_settings",
        description = "Open the system page where the user can grant or deny all-files access. Dastyar cannot grant it silently.",
        schema = obj(),
        risk = RiskLevel.CONFIRM,
        confirm = { _, lang ->
            bi(
                lang,
                "باز کردن تنظیمات دسترسی به همهٔ فایل‌ها. دستیار این اجازه را پنهانی نمی‌گیرد.",
                "Open all-files access settings. Dastyar cannot grant this silently.",
            )
        },
    ) { _, _ ->
        ui.openAllFilesSettings()
        ToolResult(true, "Opened settings")
    }

    private fun tool(
        name: String,
        description: String,
        schema: String,
        risk: RiskLevel,
        confirm: (JSONObject, String) -> String,
        run: suspend (JSONObject, ToolContext) -> ToolResult,
    ): Registered = Registered(ToolSpec(name, description, schema), risk, confirm, run)

    private fun launcherApps(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
        return resolved.map { it.loadLabel(context.packageManager).toString() to it.activityInfo.packageName }
    }

    private fun foregroundApp(): String {
        val appOps = context.getSystemService(android.app.AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        }
        if (mode != android.app.AppOpsManager.MODE_ALLOWED) return "usage access off"
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 60_000, end)
        val recent = stats.maxByOrNull { it.lastTimeUsed }
        return recent?.packageName ?: "unknown"
    }

    private fun listenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(context.packageName) == true
    }

    private fun findCalendarId(): Long? {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
            null,
            null,
            null,
        )?.use { cursor ->
            var fallback: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val primary = cursor.getInt(1) == 1
                val access = cursor.getInt(2)
                if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                if (primary) return id
                if (fallback == null) fallback = id
            }
            return fallback
        }
        return null
    }

    private suspend fun currentLocation(): String {
        val lm = context.getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return "Location is turned off"
        val last = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        if (last != null && System.currentTimeMillis() - last.time < 120_000) return formatLocation(last)
        if (Build.VERSION.SDK_INT >= 30) {
            return suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                    if (cont.isActive) cont.resume(if (loc == null) "No fix" else formatLocation(loc))
                }
            }
        }
        return suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(formatLocation(location))
                }
                @Deprecated("legacy")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
        }
    }

    private fun formatLocation(location: Location) = "lat=${location.latitude}, lon=${location.longitude}, acc=${location.accuracy}"

    private suspend fun resolveDir(path: String, ctx: ToolContext): File? {
        if (path.isBlank() || path == ".") return context.filesDir
        val direct = resolveFile(path, ctx) ?: return null
        return if (direct.isDirectory) direct else direct.parentFile
    }

    private suspend fun resolveFile(path: String, ctx: ToolContext, createParent: Boolean = false): File? {
        if (path.isBlank()) return null
        val candidate = File(path)
        val privateRoot = context.filesDir.canonicalFile
        val cacheRoot = context.cacheDir.canonicalFile
        if (!candidate.isAbsolute) {
            val nested = File(privateRoot, path).canonicalFile
            return if (nested.path.startsWith(privateRoot.path)) nested else null
        }
        val canonical = candidate.canonicalFile
        if (canonical.path.startsWith(privateRoot.path) || canonical.path.startsWith(cacheRoot.path)) return canonical
        val tree = settings.current().safTreeUri
        if (tree != null && path.startsWith("content:")) {
            return copyTreeFile(Uri.parse(path))
        }
        val allFiles = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        if (allFiles && (canonical.path.startsWith("/storage") || canonical.path.startsWith("/sdcard"))) {
            if (createParent) canonical.parentFile?.mkdirs()
            return canonical
        }
        if (tree != null) {
            val relative = path.removePrefix("/").substringAfterLast('/')
            return materializeTreeFile(Uri.parse(tree), relative, createParent)
        }
        return null
    }

    private fun copyTreeFile(uri: Uri): File? = runCatching {
        val dest = File(context.cacheDir, "saf-${System.currentTimeMillis()}.txt")
        context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
        dest
    }.getOrNull()

    private fun materializeTreeFile(tree: Uri, name: String, create: Boolean): File? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        var found: Uri? = null
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
            ?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIdx) == name) {
                        found = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(idIdx))
                        break
                    }
                }
            }
        val uri = found ?: if (create) {
            DocumentsContract.createDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, docId), "text/plain", name)
        } else {
            null
        } ?: return null
        val dest = File(context.cacheDir, name.substringAfterLast('/'))
        context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
        dest
    }.getOrNull()

    private data class Registered(
        val spec: ToolSpec,
        val risk: RiskLevel,
        val confirm: (JSONObject, String) -> String,
        val run: suspend (JSONObject, ToolContext) -> ToolResult,
    )

    private companion object {
        fun str() = """{"type":"string"}"""
        fun int() = """{"type":"integer"}"""
        fun bool() = """{"type":"boolean"}"""
        fun obj(vararg props: Pair<String, String>): String {
            val body = props.joinToString(",") { (key, value) -> "\"$key\":$value" }
            return """{"type":"object","properties":{$body}}"""
        }
    }
}

