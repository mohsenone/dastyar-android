package app.dastyar.agent.tools

/**
 * Android 13+ blocks sideloaded apps from SMS, call log, accessibility, and notification
 * listener access until the user allows restricted settings. There is no public Intent for that flag.
 */
object RestrictedSettings {
    const val MIN_SDK = 33

    fun applies(sdk: Int): Boolean = sdk >= MIN_SDK

    fun touchesRuntime(permissions: Collection<String>): Boolean =
        permissions.any { it in Perms.RESTRICTED_RUNTIME }

    fun likely(
        sdk: Int,
        smsGranted: Boolean,
        callLogGranted: Boolean,
        accessibilityOn: Boolean,
        listenerOn: Boolean,
    ): Boolean = applies(sdk) && (!smsGranted || !callLogGranted || !accessibilityOn || !listenerOn)

    fun guide(lang: String): String = if (lang.startsWith("fa")) guideFa() else guideEn()

    fun guideFa(): String = """
        اجازه به تنظیمات محدود (برای نصب از بیرون فروشگاه):
        1. یک‌بار تلاش کنید اجازهٔ پیامک را بدهید یا دسترس‌پذیری را روشن کنید (ممکن است سیستم رد کند یا گزینه خاکستری باشد؛ این طبیعی است).
        2. دکمهٔ «باز کردن اطلاعات برنامه» را بزنید (یا: تنظیمات → برنامه‌ها → دستیار).
        3. در گوشهٔ بالا منوی ⋮ (سه نقطه) را باز کنید.
        4. «Allow restricted settings» / «اجازه به تنظیمات محدود» / «مجاز کردن تنظیمات محدود» را انتخاب کنید. (متن دقیق به زبان و پوستهٔ گوشی بستگی دارد.)
        5. به همین صفحه برگردید و دوباره هر اجازه را روشن کنید: پیامک، دسترس‌پذیری (تنظیمات → دسترس‌پذیری → دستیار)، دسترسی به اعلان‌ها.
        6. در اندروید ۱۵ این مرحله سخت‌تر است؛ اگر منوی ⋮ مورد را نشان نداد، یک‌بار دیگر تلاش برای فعال‌سازی را تکرار کنید تا گزینه ظاهر شود.
    """.trimIndent()

    fun guideEn(): String = """
        Allow restricted settings (install from outside the store):
        1. Try once to grant SMS or turn Accessibility on. The system may refuse it or grey out the switch. That is expected.
        2. Tap Open app info (or Settings → Apps → Dastyar).
        3. Open the ⋮ menu in the top corner.
        4. Choose Allow restricted settings. The exact words depend on the phone language and skin.
        5. Come back to this screen and turn each access on again: SMS, Accessibility (Settings → Accessibility → Dastyar), and notification access.
        6. On Android 15 this step is stricter. If ⋮ does not list the item, try the blocked switch once more until the item appears.
    """.trimIndent()
}

object ComponentLists {
    fun listed(flat: String?, packageName: String, className: String): Boolean {
        if (flat.isNullOrBlank()) return false
        val short = className.substringAfterLast('.')
        return flat.split(':').any { raw ->
            val entry = raw.trim()
            entry.equals("$packageName/$className", ignoreCase = true) ||
                entry.equals("$packageName/.$short", ignoreCase = true) ||
                entry.endsWith("/$className") ||
                entry.endsWith("/.$short")
        }
    }
}

object PermissionMessages {
    fun denied(lang: String, permissions: Collection<String>, sdk: Int): String {
        val fa = lang.startsWith("fa")
        val base = if (fa) "اجازه داده نشد." else "Permission denied."
        if (!RestrictedSettings.applies(sdk) || !RestrictedSettings.touchesRuntime(permissions)) {
            return if (fa) "$base از صفحهٔ اجازه‌ها در تنظیمات می‌توانید دوباره تلاش کنید." else "$base Grant it from the Permissions screen in Settings, then retry."
        }
        val lead = if (fa) {
            "$base برای نصب از بیرون فروشگاه، اندروید ۱۳ و جدیدتر پیامک و گزارش تماس را تا اجازه به تنظیمات محدود نمی‌دهد. بعد از آن دوباره تلاش کنید."
        } else {
            "$base On Android 13 and later, a sideloaded app cannot receive SMS or call-log access until you allow restricted settings. Then retry."
        }
        return lead + "\n" + RestrictedSettings.guide(lang)
    }
}
