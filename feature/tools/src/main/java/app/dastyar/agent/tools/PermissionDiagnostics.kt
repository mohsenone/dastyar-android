package app.dastyar.agent.tools

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class AccessState {
    GRANTED,
    MISSING,
    NEEDS_SETTINGS,
    MAYBE_RESTRICTED,
}

data class AccessItem(
    val id: String,
    val state: AccessState,
    val titleFa: String,
    val titleEn: String,
    val whyFa: String,
    val whyEn: String,
    val nextFa: String,
    val nextEn: String,
)

data class AccessReport(
    val sdk: Int,
    val restrictedLikely: Boolean,
    val items: List<AccessItem>,
) {
    fun render(lang: String): String {
        val fa = lang.startsWith("fa")
        val text = StringBuilder()
        if (restrictedLikely) {
            text.append(
                if (fa) "تنظیمات محدود احتمالاً مانع اجازهٔ پیامک، گزارش تماس، دسترس‌پذیری یا اعلان است."
                else "Restricted settings may be blocking SMS, call log, accessibility, or notification access.",
            )
            text.append("\n")
            text.append(RestrictedSettings.guide(lang))
            text.append("\n\n")
        }
        items.forEach { item ->
            text.append(item.id).append(": ").append(item.state.name).append(" — ")
            text.append(if (fa) item.titleFa else item.titleEn).append('\n')
            text.append(if (fa) item.whyFa else item.whyEn).append('\n')
            text.append(if (fa) item.nextFa else item.nextEn).append("\n\n")
        }
        return text.toString().trim()
    }
}

interface AccessProbe {
    val sdkInt: Int
    fun runtimeGranted(permission: String): Boolean
    val accessibilityEnabled: Boolean
    val notificationListenerEnabled: Boolean
    val notificationListenerConnected: Boolean
    val overlayGranted: Boolean
    val allFilesGranted: Boolean
    val safTreeGranted: Boolean
    val exactAlarmGranted: Boolean
    val usageStatsGranted: Boolean
    val batteryUnrestricted: Boolean
}

object PermissionDiagnostics {
    const val A11Y_CLASS = "app.dastyar.agent.tools.DastyarAccessibilityService"
    const val LISTENER_CLASS = "app.dastyar.agent.tools.DastyarNotificationListener"

    fun aggregate(probe: AccessProbe): AccessReport {
        val smsGranted = listOf(Perms.READ_SMS, Perms.SEND_SMS, Perms.RECEIVE_SMS).all { probe.runtimeGranted(it) }
        val callLogGranted = probe.runtimeGranted(Perms.READ_CALL_LOG)
        val items = listOf(
            runtimeItem(
                probe,
                id = "notifications",
                permission = Perms.POST_NOTIFICATIONS,
                requiredFrom = 33,
                restricted = false,
                titleFa = "اعلان خود دستیار",
                titleEn = "Dastyar notifications",
                whyFa = "وضعیت کار در پس‌زمینه و توقف ابزارها روی اعلان دیده می‌شود.",
                whyEn = "Background work and Pause tools show up as a notification.",
            ),
            groupItem(
                probe,
                id = "sms",
                granted = smsGranted,
                restricted = true,
                titleFa = "پیامک",
                titleEn = "SMS",
                whyFa = "خواندن و فرستادن پیامک فقط با اجازهٔ شما و تأیید هر ارسال.",
                whyEn = "Reading and sending SMS needs your grant, and each send is confirmed.",
            ),
            runtimeItem(
                probe,
                id = "contacts",
                permission = Perms.READ_CONTACTS,
                extra = Perms.WRITE_CONTACTS,
                restricted = false,
                titleFa = "مخاطبین",
                titleEn = "Contacts",
                whyFa = "جستجو و افزودن مخاطب.",
                whyEn = "Search and add contacts.",
            ),
            runtimeItem(
                probe,
                id = "call_phone",
                permission = Perms.CALL_PHONE,
                restricted = false,
                titleFa = "تماس",
                titleEn = "Phone calls",
                whyFa = "تماس مستقیم فقط بعد از تأیید شما.",
                whyEn = "Placing a call happens only after you confirm.",
            ),
            groupItem(
                probe,
                id = "call_log",
                granted = callLogGranted,
                restricted = true,
                titleFa = "گزارش تماس",
                titleEn = "Call log",
                whyFa = "خواندن تماس‌های اخیر.",
                whyEn = "Read recent calls.",
            ),
            runtimeItem(
                probe,
                id = "calendar",
                permission = Perms.READ_CALENDAR,
                extra = Perms.WRITE_CALENDAR,
                restricted = false,
                titleFa = "تقویم",
                titleEn = "Calendar",
                whyFa = "دیدن و ساختن رویداد.",
                whyEn = "Read and create events.",
            ),
            runtimeItem(
                probe,
                id = "camera",
                permission = Perms.CAMERA,
                restricted = false,
                titleFa = "دوربین",
                titleEn = "Camera",
                whyFa = "عکس و ویدیو فقط وقتی خودتان بخواهید.",
                whyEn = "Photo and video only when you ask.",
            ),
            runtimeItem(
                probe,
                id = "microphone",
                permission = Perms.RECORD_AUDIO,
                restricted = false,
                titleFa = "میکروفون",
                titleEn = "Microphone",
                whyFa = "گفتار به متن با موتور خود گوشی.",
                whyEn = "Speech to text uses the engine on this phone.",
            ),
            locationItem(probe),
            backgroundLocationItem(probe),
            runtimeItem(
                probe,
                id = "phone_state",
                permission = Perms.READ_PHONE_STATE,
                restricted = false,
                titleFa = "وضعیت تلفن",
                titleEn = "Phone state",
                whyFa = "وضعیت خط برای ابزارهایی که به آن نیاز دارند.",
                whyEn = "Line state for tools that need it.",
            ),
            specialItem(
                probe,
                id = "accessibility",
                on = probe.accessibilityEnabled,
                restrictedWhenOff = true,
                titleFa = "دسترس‌پذیری",
                titleEn = "Accessibility",
                whyFa = "خواندن صفحه و کار با برنامه‌های دیگر فقط پس از توضیح و تأیید هر حرکت.",
                whyEn = "Read the screen and operate other apps only after the disclosure and a confirmation for each gesture.",
                nextOnFa = "روشن است. از تنظیمات سیستم خاموشش کنید اگر دیگر نمی‌خواهید.",
                nextOnEn = "On. Turn it off in system Accessibility settings if you no longer want it.",
                nextOffFa = "اول صفحهٔ توضیح را بخوانید، بعد در تنظیمات سیستم دستیار را روشن کنید.",
                nextOffEn = "Read the disclosure first, then turn Dastyar on in system Accessibility settings.",
            ),
            listenerItem(probe),
            specialItem(
                probe,
                id = "overlay",
                on = probe.overlayGranted,
                restrictedWhenOff = false,
                titleFa = "نمایش روی برنامه‌ها",
                titleEn = "Draw over other apps",
                whyFa = "حباب وضعیت. لمس بیرون حباب به برنامهٔ زیرین می‌رسد.",
                whyEn = "Status bubble. Touches outside it reach the app underneath.",
                nextOnFa = "داده شده.",
                nextOnEn = "Granted.",
                nextOffFa = "صفحهٔ نمایش روی برنامه‌ها را باز کنید و دستیار را مجاز کنید.",
                nextOffEn = "Open the overlay page and allow Dastyar.",
            ),
            filesItem(probe),
            safItem(probe),
            alarmItem(probe),
            specialItem(
                probe,
                id = "usage_stats",
                on = probe.usageStatsGranted,
                restrictedWhenOff = false,
                titleFa = "آمار استفاده",
                titleEn = "Usage access",
                whyFa = "دانستن اینکه کدام برنامه جلو است.",
                whyEn = "See which app is in the foreground.",
                nextOnFa = "داده شده.",
                nextOnEn = "Granted.",
                nextOffFa = "در دسترسی به مصرف، دستیار را روشن کنید.",
                nextOffEn = "Turn Dastyar on under usage access.",
            ),
            specialItem(
                probe,
                id = "battery",
                on = probe.batteryUnrestricted,
                restrictedWhenOff = false,
                titleFa = "بهینه‌سازی باتری",
                titleEn = "Battery optimization",
                whyFa = "روال‌ها اگر باتری برنامه را بخواباند ممکن است دیر اجرا شوند.",
                whyEn = "Routines can run late if the system sleeps the app.",
                nextOnFa = "محدود نیست.",
                nextOnEn = "Unrestricted.",
                nextOffFa = "فهرست باتری را باز کنید و برای دستیار «محدود نشود» را بگذارید.",
                nextOffEn = "Open the battery list and set Dastyar to unrestricted.",
            ),
        )
        val restrictedLikely = RestrictedSettings.likely(
            sdk = probe.sdkInt,
            smsGranted = smsGranted,
            callLogGranted = callLogGranted,
            accessibilityOn = probe.accessibilityEnabled,
            listenerOn = probe.notificationListenerEnabled,
        )
        return AccessReport(probe.sdkInt, restrictedLikely, items)
    }

    fun inspect(context: Context, safTreeGranted: Boolean): AccessReport =
        aggregate(AndroidAccessProbe(context, safTreeGranted))

    private fun runtimeItem(
        probe: AccessProbe,
        id: String,
        permission: String,
        extra: String? = null,
        requiredFrom: Int = 1,
        restricted: Boolean,
        titleFa: String,
        titleEn: String,
        whyFa: String,
        whyEn: String,
    ): AccessItem {
        if (probe.sdkInt < requiredFrom) {
            return item(id, AccessState.GRANTED, titleFa, titleEn, whyFa, whyEn, "در این اندروید لازم نیست.", "Not required on this Android version.")
        }
        val granted = probe.runtimeGranted(permission) && (extra == null || probe.runtimeGranted(extra))
        return groupItem(probe, id, granted, restricted, titleFa, titleEn, whyFa, whyEn)
    }

    private fun groupItem(
        probe: AccessProbe,
        id: String,
        granted: Boolean,
        restricted: Boolean,
        titleFa: String,
        titleEn: String,
        whyFa: String,
        whyEn: String,
    ): AccessItem {
        val state = when {
            granted -> AccessState.GRANTED
            restricted && RestrictedSettings.applies(probe.sdkInt) -> AccessState.MAYBE_RESTRICTED
            else -> AccessState.MISSING
        }
        val next = nextForRuntime(state)
        return item(id, state, titleFa, titleEn, whyFa, whyEn, next.first, next.second)
    }

    private fun locationItem(probe: AccessProbe): AccessItem {
        val granted = probe.runtimeGranted(Perms.ACCESS_FINE_LOCATION) || probe.runtimeGranted(Perms.ACCESS_COARSE_LOCATION)
        val state = if (granted) AccessState.GRANTED else AccessState.MISSING
        val next = nextForRuntime(state)
        return item(
            "location",
            state,
            "موقعیت",
            "Location",
            "موقعیت تقریبی یا دقیق وقتی ابزار بخواهد.",
            "Coarse or fine location when a tool asks.",
            next.first,
            next.second,
        )
    }

    private fun backgroundLocationItem(probe: AccessProbe): AccessItem {
        val foreground = probe.runtimeGranted(Perms.ACCESS_FINE_LOCATION) || probe.runtimeGranted(Perms.ACCESS_COARSE_LOCATION)
        if (probe.sdkInt < 29) {
            val state = if (foreground) AccessState.GRANTED else AccessState.MISSING
            return item(
                "background_location",
                state,
                "موقعیت پس‌زمینه",
                "Background location",
                "در این اندروید موقعیت پس‌زمینه جدا نیست.",
                "Background location is not a separate grant on this Android version.",
                if (foreground) "با موقعیت معمولی داده می‌شود." else "اول موقعیت را بدهید.",
                if (foreground) "Included with location." else "Grant location first.",
            )
        }
        val granted = probe.runtimeGranted(Perms.ACCESS_BACKGROUND_LOCATION)
        val state = when {
            granted -> AccessState.GRANTED
            !foreground -> AccessState.MISSING
            else -> AccessState.NEEDS_SETTINGS
        }
        val nextFa = when (state) {
            AccessState.GRANTED -> "داده شده."
            AccessState.MISSING -> "اول موقعیت دقیق یا تقریبی را بدهید، بعد موقعیت پس‌زمینه را."
            else -> "اجازهٔ پس‌زمینه را جداگانه بپذیرید. می‌توانید رد کنید."
        }
        val nextEn = when (state) {
            AccessState.GRANTED -> "Granted."
            AccessState.MISSING -> "Grant fine or coarse location first, then background location."
            else -> "Accept the separate background grant. You can deny it."
        }
        return item(
            "background_location",
            state,
            "موقعیت پس‌زمینه",
            "Background location",
            "فقط برای روالی که واقعاً لازم دارد.",
            "Only for a routine that truly needs it.",
            nextFa,
            nextEn,
        )
    }

    private fun listenerItem(probe: AccessProbe): AccessItem {
        val state = when {
            probe.notificationListenerEnabled && probe.notificationListenerConnected -> AccessState.GRANTED
            probe.notificationListenerEnabled -> AccessState.NEEDS_SETTINGS
            RestrictedSettings.applies(probe.sdkInt) -> AccessState.MAYBE_RESTRICTED
            else -> AccessState.NEEDS_SETTINGS
        }
        val nextFa = when (state) {
            AccessState.GRANTED -> "وصل است."
            AccessState.NEEDS_SETTINGS -> if (probe.notificationListenerEnabled) {
                "روشن است ولی وصل نیست. یک‌بار خاموش و روشن کنید."
            } else {
                "دسترسی به اعلان را باز کنید و دستیار را روشن کنید."
            }
            else -> "اگر کلید خاکستری است یا فوراً رد می‌شود، اول اجازه به تنظیمات محدود را بدهید، بعد اعلان را روشن کنید."
        }
        val nextEn = when (state) {
            AccessState.GRANTED -> "Connected."
            AccessState.NEEDS_SETTINGS -> if (probe.notificationListenerEnabled) {
                "Enabled but not connected. Toggle it off and on."
            } else {
                "Open notification access and turn Dastyar on."
            }
            else -> "If the switch is grey or the grant fails at once, allow restricted settings, then turn notification access on."
        }
        return item(
            "notification_listener",
            state,
            "دسترسی به اعلان‌ها",
            "Notification access",
            "خواندن اعلانی که خودتان می‌بینید، از جمله واتساپ و جیمیل. رمز یک‌بارمصرف پوشیده می‌شود. پاسخ خودکار نیست.",
            "Read notifications you can already see, including WhatsApp and Gmail. One-time codes are redacted. Nothing is auto-replied.",
            nextFa,
            nextEn,
        )
    }

    private fun filesItem(probe: AccessProbe): AccessItem {
        if (probe.sdkInt < 30) {
            return item(
                "all_files",
                AccessState.GRANTED,
                "همهٔ فایل‌ها",
                "All files",
                "در این اندروید این اجازهٔ جدا وجود ندارد. پوشه را با انتخابگر برگزینید.",
                "This separate grant does not exist on this Android version. Pick a folder with the system picker.",
                "از انتخاب پوشه استفاده کنید.",
                "Use the folder picker.",
            )
        }
        return specialItem(
            probe,
            id = "all_files",
            on = probe.allFilesGranted,
            restrictedWhenOff = false,
            titleFa = "همهٔ فایل‌ها",
            titleEn = "All files",
            whyFa = "خواندن مسیرهای حافظه. دستیار این اجازه را پنهانی نمی‌گیرد.",
            whyEn = "Read storage paths. Dastyar cannot grant this silently.",
            nextOnFa = "داده شده.",
            nextOnEn = "Granted.",
            nextOffFa = "صفحهٔ دسترسی به همهٔ فایل‌ها را باز کنید.",
            nextOffEn = "Open the all-files access page.",
        )
    }

    private fun safItem(probe: AccessProbe): AccessItem {
        val state = if (probe.safTreeGranted) AccessState.GRANTED else AccessState.NEEDS_SETTINGS
        return item(
            "saf",
            state,
            "پوشهٔ انتخاب‌شده",
            "Chosen folder",
            "پوشه‌ای که با انتخابگر سیستم به دستیار می‌سپارید.",
            "A folder you give Dastyar with the system picker.",
            if (probe.safTreeGranted) "یک پوشه ذخیره شده است." else "یک پوشه انتخاب کنید.",
            if (probe.safTreeGranted) "A folder is saved." else "Choose a folder.",
        )
    }

    private fun alarmItem(probe: AccessProbe): AccessItem {
        if (probe.sdkInt < 31) {
            return item(
                "exact_alarm",
                AccessState.GRANTED,
                "زنگ دقیق",
                "Exact alarms",
                "در این اندروید اجازهٔ جدا لازم نیست.",
                "No separate grant on this Android version.",
                "لازم نیست.",
                "Not required.",
            )
        }
        return specialItem(
            probe,
            id = "exact_alarm",
            on = probe.exactAlarmGranted,
            restrictedWhenOff = false,
            titleFa = "زنگ دقیق",
            titleEn = "Exact alarms",
            whyFa = "روال‌ها سر ساعت. بدون این اجازه زنگ تقریبی است.",
            whyEn = "Routines fire on time. Without this grant the alarm is inexact.",
            nextOnFa = "داده شده.",
            nextOnEn = "Granted.",
            nextOffFa = "صفحهٔ زنگ دقیق را باز کنید و دستیار را مجاز کنید.",
            nextOffEn = "Open exact alarms and allow Dastyar.",
        )
    }

    private fun specialItem(
        probe: AccessProbe,
        id: String,
        on: Boolean,
        restrictedWhenOff: Boolean,
        titleFa: String,
        titleEn: String,
        whyFa: String,
        whyEn: String,
        nextOnFa: String,
        nextOnEn: String,
        nextOffFa: String,
        nextOffEn: String,
    ): AccessItem {
        val state = when {
            on -> AccessState.GRANTED
            restrictedWhenOff && RestrictedSettings.applies(probe.sdkInt) -> AccessState.MAYBE_RESTRICTED
            else -> AccessState.NEEDS_SETTINGS
        }
        val nextFa = if (on) nextOnFa else if (state == AccessState.MAYBE_RESTRICTED) {
            "$nextOffFa اگر سیستم رد کرد، راهنمای تنظیمات محدود را انجام دهید و برگردید."
        } else {
            nextOffFa
        }
        val nextEn = if (on) nextOnEn else if (state == AccessState.MAYBE_RESTRICTED) {
            "$nextOffEn If the system refuses, follow the restricted-settings steps and come back."
        } else {
            nextOffEn
        }
        return item(id, state, titleFa, titleEn, whyFa, whyEn, nextFa, nextEn)
    }

    private fun nextForRuntime(state: AccessState): Pair<String, String> = when (state) {
        AccessState.GRANTED -> "داده شده." to "Granted."
        AccessState.MISSING -> "دکمهٔ اجازه را بزنید و گفتگوی سیستم را بپذیرید." to "Tap Grant and accept the system dialog."
        AccessState.NEEDS_SETTINGS -> "صفحهٔ تنظیمات را باز کنید." to "Open the settings page."
        AccessState.MAYBE_RESTRICTED ->
            "یک‌بار اجازه را بخواهید. اگر فوراً رد شد، اطلاعات برنامه را باز کنید، منوی ⋮، اجازه به تنظیمات محدود، بعد دوباره تلاش کنید." to
                "Request the grant once. If it is denied immediately, open app info, the ⋮ menu, Allow restricted settings, then try again."
    }

    private fun item(
        id: String,
        state: AccessState,
        titleFa: String,
        titleEn: String,
        whyFa: String,
        whyEn: String,
        nextFa: String,
        nextEn: String,
    ) = AccessItem(id, state, titleFa, titleEn, whyFa, whyEn, nextFa, nextEn)
}

internal class AndroidAccessProbe(
    private val context: Context,
    override val safTreeGranted: Boolean,
) : AccessProbe {
    override val sdkInt: Int = Build.VERSION.SDK_INT

    override fun runtimeGranted(permission: String): Boolean = runCatching {
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    override val accessibilityEnabled: Boolean
        get() = runCatching {
            val resolver = context.contentResolver
            if (Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
            val flat = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ComponentLists.listed(flat, context.packageName, PermissionDiagnostics.A11Y_CLASS)
        }.getOrDefault(false)

    override val notificationListenerEnabled: Boolean
        get() = runCatching {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ComponentLists.listed(flat, context.packageName, PermissionDiagnostics.LISTENER_CLASS)
        }.getOrDefault(false)

    override val notificationListenerConnected: Boolean
        get() = DastyarNotificationListener.instance != null

    override val overlayGranted: Boolean
        get() = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    override val allFilesGranted: Boolean
        get() = if (Build.VERSION.SDK_INT < 30) true else runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)

    override val exactAlarmGranted: Boolean
        get() = if (Build.VERSION.SDK_INT < 31) {
            true
        } else {
            runCatching { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }.getOrDefault(false)
        }

    override val usageStatsGranted: Boolean
        get() = runCatching {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)

    override val batteryUnrestricted: Boolean
        get() = runCatching {
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
}
