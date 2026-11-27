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
    val group: String = AccessGroups.SYSTEM,
    val permissions: List<String> = emptyList(),
    val target: String = PermissionTargets.APP_INFO,
    /** False for optional nags such as all-files access and Shizuku. Fix everything skips these. */
    val nag: Boolean = true,
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
    val writeSettingsGranted: Boolean get() = false
    val dndGranted: Boolean get() = false

    /** missing, unauthorized, ready, or unknown. Shizuku is optional. */
    val shizuku: String get() = ShizukuStatusHook.UNKNOWN

    fun permanentlyDenied(permission: String): Boolean = PermissionMemory.isPermanent(permission)
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
                whyFa = "گفتگوی صوتی با دستیار. فقط وقتی میکروفون را بزنید پرسیده می‌شود.",
                whyEn = "Voice conversation. Asked only when you tap the microphone.",
            ),
            locationItem(probe),
            backgroundLocationItem(probe),
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
            mediaItem(probe),
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
                nextOffFa = "درخواست باتری را بپذیرید، یا فهرست را باز کنید و «محدود نشود» را بگذارید.",
                nextOffEn = "Accept the battery request, or open the list and set Dastyar to unrestricted.",
            ),
            specialItem(
                probe,
                id = "write_settings",
                on = probe.writeSettingsGranted,
                restrictedWhenOff = false,
                titleFa = "نوشتن تنظیمات",
                titleEn = "Modify system settings",
                whyFa = "روشنایی صفحه. بدون این اجازه دستیار فقط صفحهٔ سیستم را باز می‌کند.",
                whyEn = "Screen brightness. Without it Dastyar only opens the system page.",
                nextOnFa = "داده شده.",
                nextOnEn = "Granted.",
                nextOffFa = "صفحهٔ نوشتن تنظیمات را باز کنید و دستیار را مجاز کنید.",
                nextOffEn = "Open modify system settings and allow Dastyar.",
            ),
            specialItem(
                probe,
                id = "dnd",
                on = probe.dndGranted,
                restrictedWhenOff = false,
                titleFa = "مزاحم نشوید",
                titleEn = "Do Not Disturb",
                whyFa = "عوض کردن حالت مزاحم نشوید. بدون اجازه فقط صفحهٔ سیاست اعلان باز می‌شود.",
                whyEn = "Change Do Not Disturb. Without the grant Dastyar only opens the policy page.",
                nextOnFa = "داده شده.",
                nextOnEn = "Granted.",
                nextOffFa = "دسترسی سیاست اعلان را باز کنید و دستیار را مجاز کنید.",
                nextOffEn = "Open notification policy access and allow Dastyar.",
            ),
            shizukuItem(probe),
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
            return item(id, AccessState.GRANTED, titleFa, titleEn, whyFa, whyEn, "در این اندروید لازم نیست.", "Not required on this Android version.", probe.sdkInt)
        }
        val names = listOfNotNull(permission, extra)
        val granted = names.all { probe.runtimeGranted(it) }
        return groupItem(probe, id, granted, restricted, names, titleFa, titleEn, whyFa, whyEn)
    }

    private fun groupItem(
        probe: AccessProbe,
        id: String,
        granted: Boolean,
        restricted: Boolean,
        permissions: List<String> = emptyList(),
        titleFa: String,
        titleEn: String,
        whyFa: String,
        whyEn: String,
    ): AccessItem {
        val missing = permissions.filter { !probe.runtimeGranted(it) }
        val state = RuntimeGrant.state(granted, restricted, probe.sdkInt, missing) { probe.permanentlyDenied(it) }
        val next = nextForRuntime(state)
        return item(id, state, titleFa, titleEn, whyFa, whyEn, next.first, next.second, probe.sdkInt)
    }

    private fun locationItem(probe: AccessProbe): AccessItem {
        val names = listOf(Perms.ACCESS_FINE_LOCATION, Perms.ACCESS_COARSE_LOCATION)
        val granted = LocationAccess.foregroundGranted { probe.runtimeGranted(it) }
        val state = RuntimeGrant.state(granted, restricted = false, probe.sdkInt, names.filter { !probe.runtimeGranted(it) }) {
            probe.permanentlyDenied(it)
        }
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
            probe.sdkInt,
        )
    }

    private fun backgroundLocationItem(probe: AccessProbe): AccessItem {
        val foreground = LocationAccess.foregroundGranted { probe.runtimeGranted(it) }
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
                probe.sdkInt,
            )
        }
        val granted = probe.runtimeGranted(Perms.ACCESS_BACKGROUND_LOCATION)
        val state = LocationAccess.backgroundState(probe.sdkInt) { probe.runtimeGranted(it) }
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
            "Only for a routine that truly needs it. It is never on the first location prompt.",
            nextFa,
            nextEn,
            probe.sdkInt,
        )
    }

    private fun mediaItem(probe: AccessProbe): AccessItem {
        val names = MediaAccess.requestPermissions(probe.sdkInt)
        val base = MediaAccess.state(probe.sdkInt) { probe.runtimeGranted(it) }
        val state = if (base == AccessState.GRANTED) {
            AccessState.GRANTED
        } else if (names.isNotEmpty() && names.all { probe.permanentlyDenied(it) }) {
            AccessState.NEEDS_SETTINGS
        } else {
            base
        }
        val limited = MediaAccess.limited(probe.sdkInt) { probe.runtimeGranted(it) }
        val next = if (limited) {
            "دسترسی محدود به عکس‌ها داده شده. انتخابگر سیستم برای یک فایل کافی است." to
                "Limited photo access is granted. The system picker is enough for a single file."
        } else {
            nextForRuntime(state)
        }
        return item(
            "media",
            state,
            "عکس و رسانه",
            "Photos and media",
            "خواندن کتابخانه وقتی ابزاری فهرست می‌خواهد. پیوست یک عکس از انتخابگر سیستم اجازهٔ کلی نمی‌خواهد.",
            "Read the library when a tool lists it. Attaching one photo uses the system picker and does not need this grant.",
            next.first,
            next.second,
            probe.sdkInt,
        )
    }

    private fun shizukuItem(probe: AccessProbe): AccessItem {
        val state = when (probe.shizuku) {
            ShizukuStatusHook.READY -> AccessState.GRANTED
            ShizukuStatusHook.UNAUTHORIZED -> AccessState.NEEDS_SETTINGS
            else -> AccessState.MISSING
        }
        val nextFa = when (probe.shizuku) {
            ShizukuStatusHook.READY -> "آماده است. اختیاری است و جای اجازه‌های معمولی نیست."
            ShizukuStatusHook.UNAUTHORIZED -> "شیزوکو نصب است ولی دستیار مجاز نیست. از خود شیزوکو اجازه بدهید."
            ShizukuStatusHook.MISSING -> "شیزوکو نصب نیست. این یک کمبود صادقانه است، نه یک اجازهٔ پنهان."
            else -> "وضعیت شیزوکو از اینجا معلوم نشد."
        }
        val nextEn = when (probe.shizuku) {
            ShizukuStatusHook.READY -> "Ready. It is optional and does not replace normal permissions."
            ShizukuStatusHook.UNAUTHORIZED -> "Shizuku is installed but Dastyar is not authorized. Allow it inside Shizuku."
            ShizukuStatusHook.MISSING -> "Shizuku is not installed. That is an honest gap, not a hidden permission."
            else -> "Shizuku status could not be checked from here."
        }
        return item(
            "shizuku",
            state,
            "شیزوکو",
            "Shizuku",
            "اختیاری. برای چند فرمان محدود settings و pm و am. اگر نباشد بقیهٔ دستیار کار می‌کند.",
            "Optional. A few narrow settings, pm, and am commands. The rest of Dastyar works without it.",
            nextFa,
            nextEn,
            probe.sdkInt,
        )
    }

    private fun listenerItem(probe: AccessProbe): AccessItem {
        val listed = probe.notificationListenerEnabled
        val connected = probe.notificationListenerConnected
        val restrictedSdk = RestrictedSettings.applies(probe.sdkInt)
        // A secure-setting write can list the listener while Android 13+ still refuses to bind a
        // sideloaded app until Allow restricted settings. That is not a granted listener.
        val state = when {
            listed && connected -> AccessState.GRANTED
            listed && restrictedSdk -> AccessState.MAYBE_RESTRICTED
            listed -> AccessState.NEEDS_SETTINGS
            restrictedSdk -> AccessState.MAYBE_RESTRICTED
            else -> AccessState.NEEDS_SETTINGS
        }
        val nextFa = when {
            state == AccessState.GRANTED -> "وصل است."
            listed && restrictedSdk ->
                "در دسترسی به اعلان نوشته شده، ولی اندروید شنونده را وصل نکرده. روی نصب از بیرون فروشگاه این معمولاً یعنی اجازه به تنظیمات محدود هنوز داده نشده. اطلاعات برنامه را باز کنید، منوی سه نقطه، اجازه به تنظیمات محدود، بعد دسترسی به اعلان را یک‌بار خاموش و روشن کنید."
            listed -> "روشن است ولی وصل نیست. یک‌بار خاموش و روشن کنید."
            state == AccessState.MAYBE_RESTRICTED ->
                "اگر کلید خاکستری است یا فوراً رد می‌شود، اول اجازه به تنظیمات محدود را بدهید، بعد اعلان را روشن کنید."
            else -> "دسترسی به اعلان را باز کنید و دستیار را روشن کنید."
        }
        val nextEn = when {
            state == AccessState.GRANTED -> "Connected."
            listed && restrictedSdk ->
                "Notification access lists Dastyar, but Android has not connected the listener. On a sideloaded install this usually means Allow restricted settings is still off. Open app info, open the three-dot menu, allow restricted settings, then turn notification access off and on."
            listed -> "Enabled but not connected. Toggle it off and on."
            state == AccessState.MAYBE_RESTRICTED ->
                "If the switch is grey or the grant fails at once, allow restricted settings, then turn notification access on."
            else -> "Open notification access and turn Dastyar on."
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
            whyFa = "اختیاری. پوشهٔ انتخاب‌شده کافی است. «رفع همه» این مورد را دوباره نمی‌پرسد.",
            whyEn = "Optional. A chosen folder is enough. Fix everything does not ask for this again.",
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
        AccessState.NEEDS_SETTINGS ->
            "گفتگوی سیستم دیگر نشان داده نمی‌شود. اطلاعات برنامه را باز کنید و اجازه را آنجا بدهید." to
                "The system will not show the dialog again. Open app settings and allow it there."
        AccessState.MAYBE_RESTRICTED ->
            "یک‌بار اجازه را بخواهید. اگر فوراً رد شد، اطلاعات برنامه را باز کنید، منوی سه نقطه، اجازه به تنظیمات محدود، بعد دوباره تلاش کنید." to
                "Request the grant once. If it is denied immediately, open app info, the three-dot menu, Allow restricted settings, then try again."
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
        sdk: Int = 1,
    ) = AccessItem(
        id = id,
        state = state,
        titleFa = titleFa,
        titleEn = titleEn,
        whyFa = whyFa,
        whyEn = whyEn,
        nextFa = nextFa,
        nextEn = nextEn,
        group = groupFor(id),
        permissions = permissionsFor(id, sdk),
        target = targetFor(id, state),
        nag = id != "all_files" && id != "shizuku",
    )

    private fun groupFor(id: String): String = when (id) {
        "sms" -> AccessGroups.MESSAGES
        "call_phone", "call_log" -> AccessGroups.CALLS
        "contacts" -> AccessGroups.CONTACTS
        "accessibility", "overlay" -> AccessGroups.APPS
        "notifications", "notification_listener" -> AccessGroups.NOTIFICATIONS
        "media", "all_files", "saf" -> AccessGroups.FILES
        "location", "background_location" -> AccessGroups.LOCATION
        "camera", "microphone" -> AccessGroups.CAPTURE
        else -> AccessGroups.SYSTEM
    }

    private fun targetFor(id: String, state: AccessState): String = when (id) {
        "accessibility" -> PermissionTargets.ACCESSIBILITY
        "notification_listener" -> PermissionTargets.LISTENER
        "overlay" -> PermissionTargets.OVERLAY
        "all_files" -> PermissionTargets.ALL_FILES
        "saf" -> PermissionTargets.SAF
        "exact_alarm" -> PermissionTargets.EXACT_ALARM
        "usage_stats" -> PermissionTargets.USAGE
        "battery" -> PermissionTargets.BATTERY
        "write_settings" -> PermissionTargets.WRITE_SETTINGS
        "dnd" -> PermissionTargets.DND
        "shizuku" -> PermissionTargets.SHIZUKU
        else -> if (state == AccessState.NEEDS_SETTINGS || state == AccessState.MAYBE_RESTRICTED) {
            PermissionTargets.APP_INFO
        } else {
            PermissionTargets.RUNTIME
        }
    }

    private fun permissionsFor(id: String, sdk: Int): List<String> = when (id) {
        "notifications" -> if (sdk >= 33) listOf(Perms.POST_NOTIFICATIONS) else emptyList()
        "sms" -> Perms.SMS_GROUP
        "contacts" -> listOf(Perms.READ_CONTACTS, Perms.WRITE_CONTACTS)
        "call_phone" -> listOf(Perms.CALL_PHONE)
        "call_log" -> listOf(Perms.READ_CALL_LOG)
        "calendar" -> listOf(Perms.READ_CALENDAR, Perms.WRITE_CALENDAR)
        "camera" -> listOf(Perms.CAMERA)
        "microphone" -> listOf(Perms.RECORD_AUDIO)
        "location" -> listOf(Perms.ACCESS_COARSE_LOCATION, Perms.ACCESS_FINE_LOCATION)
        "background_location" -> if (sdk >= 29) listOf(Perms.ACCESS_BACKGROUND_LOCATION) else emptyList()
        "media" -> MediaAccess.requestPermissions(sdk)
        else -> emptyList()
    }
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

    override val writeSettingsGranted: Boolean
        get() = runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

    override val dndGranted: Boolean
        get() = runCatching {
            context.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted
        }.getOrDefault(false)

    override val shizuku: String
        get() = runCatching { ShizukuStatusHook.read() }.getOrDefault(ShizukuStatusHook.UNKNOWN)

    override fun permanentlyDenied(permission: String): Boolean = PermissionMemory.isPermanent(permission)
}
