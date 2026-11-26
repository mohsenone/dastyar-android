package app.dastyar.agent.tools

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun extraDeviceTools(
    context: Context,
    ui: UiBridge,
    apps: AppActions,
    tool: (
        name: String,
        description: String,
        schema: String,
        risk: RiskLevel,
        confirm: (JSONObject, String) -> String,
        run: suspend (JSONObject, ToolContext) -> ToolResult,
    ) -> ToolRegistry.Registered,
): List<ToolRegistry.Registered> {
    fun str() = """{"type":"string"}"""
    fun int() = """{"type":"integer"}"""
    fun bool() = """{"type":"boolean"}"""
    fun obj(vararg props: Pair<String, String>): String {
        val body = props.joinToString(",") { (key, value) -> "\"$key\":$value" }
        return """{"type":"object","properties":{$body}}"""
    }
    return listOf(
        tool(
            "list_installed_apps",
            "List launcher apps the user installed: label and package. Read only.",
            obj("query" to str()),
            RiskLevel.SAFE,
            { args, _ -> args.optString("query").ifBlank { "all apps" } },
        ) { args, _ ->
            val q = args.optString("query").trim()
            val rows = apps.listInstalled().filter { q.isBlank() || it.first.contains(q, true) || it.second.contains(q, true) }
            val text = rows.take(200).joinToString("\n") { "${it.first} · ${it.second}" }
            ToolResult(true, text.ifBlank { "No apps" } + if (rows.size > 200) "\n…${rows.size} total" else "")
        },
        tool(
            "open_deep_link",
            "Open a deep link or an https URL, optionally forced into one package (Chrome, Telegram, Instagram, Gmail). Does not type or send.",
            obj("uri" to str(), "package" to str()),
            RiskLevel.CONFIRM,
            { args, _ -> args.optString("uri") },
        ) { args, ctx ->
            val uri = args.optString("uri").trim()
            val pkg = args.optString("package").trim().ifBlank { null }
            val opened = apps.openDeepLink(uri, pkg)
                ?: return@tool ToolResult(false, bi(ctx.lang, "پیوند باز نشد.", "The link did not open."))
            ToolResult(true, "Opened $opened")
        },
        tool(
            "wait_for_element",
            "Wait until accessibility finds a node by text, view id, or content description. Does not tap. Times out in at most 20 seconds.",
            obj("text" to str(), "view_id" to str(), "content_description" to str(), "match" to str(), "timeout_ms" to int()),
            RiskLevel.CONFIRM,
            { args, lang ->
                bi(lang, "منتظر عنصر: ${args.optString("text")} ${args.optString("view_id")}", "Wait for ${args.optString("text")} ${args.optString("view_id")}")
            },
        ) { args, ctx ->
            val service = DastyarAccessibilityService.instance
                ?: return@tool ToolResult(false, bi(ctx.lang, "دسترس‌پذیری خاموش است.", "Accessibility is off."))
            val timeout = args.optLong("timeout_ms", 5_000).coerceIn(200, 20_000)
            val exact = args.optString("match").equals("exact", true)
            val deadline = System.currentTimeMillis() + timeout
            var found = "not found"
            while (System.currentTimeMillis() < deadline) {
                if (AutomationGate.paused) return@tool ToolResult(false, "Paused")
                found = withContext(Dispatchers.Main.immediate) {
                    service.findMatch(
                        args.optString("view_id"),
                        args.optString("text"),
                        args.optString("content_description"),
                        exact,
                    )
                }
                if (found != "not found") return@tool ToolResult(true, found)
                delay(250)
            }
            ToolResult(false, "timed out")
        },
        tool(
            "app_whatsapp",
            "Open WhatsApp (or WhatsApp Business) on a phone number or wa.me link. Sending still uses send_whatsapp. This helper only opens the chat unless tap_send is true, which is a confirmed send and needs accessibility. The vendor UI can change.",
            obj("phone" to str(), "text" to str(), "tap_send" to bool()),
            RiskLevel.CONFIRM,
            { args, lang ->
                bi(lang, "واتساپ ${args.optString("phone")}\n${args.optString("text")}", "WhatsApp ${args.optString("phone")}\n${args.optString("text")}")
            },
        ) { args, ctx ->
            val phone = args.optString("phone").filter { it.isDigit() || it == '+' }
            val text = args.optString("text")
            val opened = apps.openWhatsApp(phone.filter { it.isDigit() }, text)
                ?: return@tool ToolResult(false, bi(ctx.lang, "واتساپ نصب نیست.", "WhatsApp is not installed."))
            if (!args.optBoolean("tap_send")) {
                return@tool ToolResult(true, bi(ctx.lang, "گفتگو در $opened باز شد. ارسال زده نشد.", "Chat opened in $opened. Send was not pressed."))
            }
            ToolResult(true, "Use send_whatsapp for a confirmed send. Opened $opened.")
        },
        tool(
            "app_telegram",
            "Open the user's Telegram app (org.telegram.messenger or the official web variant), not the bot API. Pass a username, a tg: link, or an https://t.me link. tap_send tries a confirmed accessibility tap on Send and may fail if Telegram's UI changed. This does not log into the personal account.",
            obj("username" to str(), "uri" to str(), "text" to str(), "tap_send" to bool()),
            RiskLevel.CONFIRM,
            { args, lang -> bi(lang, "تلگرام ${args.optString("username")} ${args.optString("uri")}", "Telegram ${args.optString("username")} ${args.optString("uri")}") },
        ) { args, ctx ->
            val uri = args.optString("uri").ifBlank {
                val user = args.optString("username").trim().removePrefix("@")
                if (user.isBlank()) "tg://resolve" else "https://t.me/$user"
            }
            val pkg = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")
                .firstOrNull { apps.openDeepLink(uri, it) != null }
            if (pkg == null && apps.openDeepLink(uri, null) == null) {
                return@tool ToolResult(false, bi(ctx.lang, "تلگرام نصب نیست یا پیوند باز نشد.", "Telegram is not installed, or the link did not open."))
            }
            if (!args.optBoolean("tap_send")) {
                return@tool ToolResult(true, bi(ctx.lang, "تلگرام باز شد. ارسال زده نشد. اگر صفحه درست نیست، operate_app را به کار ببرید.", "Telegram opened. Send was not pressed. If the screen is wrong, use operate_app."))
            }
            val service = DastyarAccessibilityService.instance
                ?: return@tool ToolResult(true, bi(ctx.lang, "تلگرام باز شد. دسترس‌پذیری خاموش است و ارسال زده نشد.", "Telegram opened. Accessibility is off, so Send was not pressed."))
            delay(800)
            val step = JSONObject().put("type", "set_text").put("value", args.optString("text"))
            val typed = withContext(Dispatchers.Main.immediate) { service.perform(step) }
            val send = withContext(Dispatchers.Main.immediate) {
                service.perform(JSONObject().put("type", "tap").put("content_description", "Send"))
            }
            ToolResult(true, "$typed\n$send\n${service.dumpText().take(500)}")
        },
        tool(
            "app_gmail",
            "Open Gmail's search or a mailto draft. This is the installed Gmail app, not the Gmail API (use google_gmail_* after Connectors login). tap_send is best-effort and may miss if Gmail's UI changed.",
            obj("query" to str(), "to" to str(), "subject" to str(), "body" to str(), "tap_send" to bool()),
            RiskLevel.CONFIRM,
            { args, lang -> bi(lang, "جیمیل ${args.optString("query")} ${args.optString("to")}", "Gmail ${args.optString("query")} ${args.optString("to")}") },
        ) { args, ctx ->
            val query = args.optString("query").trim()
            if (query.isNotBlank()) {
                val url = "https://mail.google.com/mail/u/0/#search/" + Uri.encode(query)
                val opened = apps.openHttps(url, "com.google.android.gm")
                    ?: return@tool ToolResult(false, bi(ctx.lang, "جیمیل باز نشد.", "Gmail did not open."))
                return@tool ToolResult(true, "Opened search in $opened")
            }
            val draft = MailLinks.draft(args.optString("to"), args.optString("subject"), args.optString("body"))
                ?: return@tool ToolResult(false, bi(ctx.lang, "گیرنده یا عبارت جستجو لازم است.", "A recipient or a search query is required."))
            val opened = apps.openMail(draft) ?: return@tool ToolResult(false, bi(ctx.lang, "برنامهٔ ایمیل نیست.", "No email app."))
            if (!args.optBoolean("tap_send")) return@tool ToolResult(true, "Draft opened in $opened. Send was not pressed.")
            ToolResult(true, "Draft opened in $opened. Prefer compose_email with tap_send, or operate_app if the Send button is not the default id.")
        },
        tool(
            "app_instagram",
            "Open Instagram on a profile or the home screen via instagram:// or https://instagram.com. The UI changes often; this does not promise a reliable Send tap. Use operate_app to read and act, and expect misses.",
            obj("username" to str(), "uri" to str()),
            RiskLevel.CONFIRM,
            { args, _ -> args.optString("uri").ifBlank { args.optString("username") }.ifBlank { "instagram" } },
        ) { args, ctx ->
            val uri = args.optString("uri").ifBlank {
                val user = args.optString("username").trim().removePrefix("@")
                if (user.isBlank()) "https://instagram.com/" else "https://instagram.com/$user"
            }
            val opened = apps.openDeepLink(uri, "com.instagram.android")
                ?: apps.openDeepLink(uri, null)
                ?: return@tool ToolResult(false, bi(ctx.lang, "اینستاگرام باز نشد.", "Instagram did not open."))
            ToolResult(true, "Opened $opened. Instagram's UI is not a stable API; use operate_app to read the screen.")
        },
        tool(
            "app_chrome",
            "Open an http(s) URL in Chrome when it is installed, otherwise the default browser.",
            obj("url" to str()),
            RiskLevel.CONFIRM,
            { args, _ -> args.optString("url") },
        ) { args, ctx ->
            val url = args.optString("url").trim()
            val opened = apps.openHttps(url, "com.android.chrome")
                ?: return@tool ToolResult(false, bi(ctx.lang, "نشانی باز نشد.", "The URL did not open."))
            ToolResult(true, "Opened $opened")
        },
        tool(
            "media_control",
            "Play, pause, next, or previous on the active media session. Requires notification access. Fails clearly when no session exists.",
            obj("action" to str()),
            RiskLevel.CONFIRM,
            { args, lang -> bi(lang, "رسانه: ${args.optString("action")}", "Media: ${args.optString("action")}") },
        ) { args, ctx ->
            val manager = context.getSystemService(MediaSessionManager::class.java)
            val sessions = try {
                manager.getActiveSessions(ComponentName(context, DastyarNotificationListener::class.java))
            } catch (_: SecurityException) {
                return@tool ToolResult(false, bi(ctx.lang, "دسترسی اعلان برای کنترل رسانه لازم است.", "Notification access is required for media control."))
            }
            val controller = sessions.firstOrNull()
                ?: return@tool ToolResult(false, bi(ctx.lang, "جلسهٔ رسانه‌ای فعال نیست.", "No active media session."))
            val controls = controller.transportControls
            when (args.optString("action").lowercase()) {
                "play" -> controls.play()
                "pause" -> controls.pause()
                "next" -> controls.skipToNext()
                "previous", "prev" -> controls.skipToPrevious()
                "stop" -> controls.stop()
                else -> return@tool ToolResult(false, "action must be play, pause, next, previous, or stop")
            }
            val state = controller.playbackState?.state
            val label = when (state) {
                PlaybackState.STATE_PLAYING -> "playing"
                PlaybackState.STATE_PAUSED -> "paused"
                else -> "state=$state"
            }
            ToolResult(true, "${controller.packageName} $label")
        },
        tool(
            "set_volume",
            "Set the music stream volume from 0 to the device maximum. This is a local change.",
            obj("level" to int()),
            RiskLevel.CONFIRM,
            { args, lang -> bi(lang, "صدا: ${args.optInt("level")}", "Volume: ${args.optInt("level")}") },
        ) { args, _ ->
            val audio = context.getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val level = args.optInt("level").coerceIn(0, max)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
            ToolResult(true, "volume=$level max=$max")
        },
        tool(
            "set_brightness",
            "Set screen brightness 0-255 when WRITE_SETTINGS is granted. Otherwise open the system write-settings page and do not claim the value changed.",
            obj("value" to int()),
            RiskLevel.CONFIRM,
            { args, lang -> bi(lang, "روشنایی ${args.optInt("value")}", "Brightness ${args.optInt("value")}") },
        ) { args, ctx ->
            if (!Settings.System.canWrite(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return@tool ToolResult(false, bi(ctx.lang, "اجازهٔ نوشتن تنظیمات نیست. صفحهٔ سیستم باز شد. روشنایی عوض نشد.", "Write-settings is not granted. Opened the system page. Brightness was not changed."))
            }
            val value = args.optInt("value", 128).coerceIn(0, 255)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
            ToolResult(true, "brightness=$value")
        },
        tool(
            "open_network_panel",
            "Android 10 and later blocks silent Wi-Fi toggles. This opens the system Internet panel. It does not claim Wi-Fi was toggled.",
            obj(),
            RiskLevel.CONFIRM,
            { _, lang -> bi(lang, "باز کردن پنل اینترنت. وای‌فای پنهانی عوض نمی‌شود.", "Open the Internet panel. Wi-Fi is not toggled silently.") },
        ) { _, ctx ->
            val action = if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult(true, bi(ctx.lang, "پنل سیستم باز شد. وای‌فای از اینجا عوض نشد.", "Opened the system panel. Wi-Fi was not changed from here."))
        },
        tool(
            "open_bluetooth_panel",
            "Opens the system Bluetooth panel. Android blocks silent Bluetooth toggles for normal apps. This does not claim Bluetooth was toggled.",
            obj(),
            RiskLevel.CONFIRM,
            { _, lang -> bi(lang, "باز کردن پنل بلوتوث.", "Open the Bluetooth panel.") },
        ) { _, ctx ->
            val action = Settings.ACTION_BLUETOOTH_SETTINGS
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult(true, bi(ctx.lang, "پنل بلوتوث باز شد. وضعیت عوض نشد.", "Opened the Bluetooth panel. The radio was not changed."))
        },
        tool(
            "set_dnd",
            "Set Do Not Disturb when notification-policy access is granted: all, priority, none, or alarms. Otherwise open the system page and do not claim the filter changed.",
            obj("filter" to str()),
            RiskLevel.CONFIRM,
            { args, lang -> bi(lang, "مزاحم نشوید: ${args.optString("filter")}", "DND: ${args.optString("filter")}") },
        ) { args, ctx ->
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.isNotificationPolicyAccessGranted) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return@tool ToolResult(false, bi(ctx.lang, "اجازهٔ سیاست اعلان نیست. صفحه باز شد. حالت عوض نشد.", "Notification policy access is off. Opened the page. DND was not changed."))
            }
            val filter = when (args.optString("filter").lowercase()) {
                "all", "none", "off" -> NotificationManager.INTERRUPTION_FILTER_ALL
                "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                "dnd", "no_interruptions", "total" -> NotificationManager.INTERRUPTION_FILTER_NONE
                else -> return@tool ToolResult(false, "filter must be all, priority, alarms, or dnd")
            }
            nm.setInterruptionFilter(filter)
            ToolResult(true, "dnd=${args.optString("filter")}")
        },
    )
}
