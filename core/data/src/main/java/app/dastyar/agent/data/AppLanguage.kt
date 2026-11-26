package app.dastyar.agent.data

import android.content.res.Resources
import android.os.Build

/**
 * Persisted language preference.
 * A missing value is English. "fa" already stored stays Persian. "system" follows the device.
 */
object AppLanguage {
    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val PERSIAN = "fa"
    const val DEFAULT = ENGLISH

    fun normalize(stored: String?): String = when {
        stored.isNullOrBlank() -> DEFAULT
        stored == SYSTEM -> SYSTEM
        stored.startsWith(PERSIAN) -> PERSIAN
        stored.startsWith(ENGLISH) -> ENGLISH
        else -> DEFAULT
    }

    /** Null means do not override the device locale list. */
    fun localeTag(stored: String?): String? = when (normalize(stored)) {
        SYSTEM -> null
        PERSIAN -> PERSIAN
        else -> ENGLISH
    }

    fun uiLang(stored: String?, deviceLanguage: String): String = when (normalize(stored)) {
        PERSIAN -> PERSIAN
        ENGLISH -> ENGLISH
        else -> if (deviceLanguage.lowercase().startsWith(PERSIAN)) PERSIAN else ENGLISH
    }
}

object Appearance {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    const val BRAND = "brand"
    const val DYNAMIC = "dynamic"

    fun theme(stored: String?): String = when (stored) {
        LIGHT, DARK -> stored
        else -> SYSTEM
    }

    fun color(stored: String?): String = if (stored == DYNAMIC) DYNAMIC else BRAND
}

fun deviceLanguageTag(): String {
    val configuration = Resources.getSystem().configuration
    val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !configuration.locales.isEmpty) {
        configuration.locales[0]?.language
    } else {
        @Suppress("DEPRECATION")
        configuration.locale?.language
    }
    return language?.takeIf { it.isNotBlank() } ?: AppLanguage.ENGLISH
}

/** Process-start snapshot so the first frame matches the stored preference. */
object LocaleGate {
    @Volatile var pref: String = AppLanguage.DEFAULT
    @Volatile var uiLang: String = AppLanguage.DEFAULT
    @Volatile var themeMode: String = Appearance.SYSTEM
    @Volatile var colorMode: String = Appearance.BRAND

    fun publish(language: String, theme: String, color: String) {
        pref = AppLanguage.normalize(language)
        uiLang = AppLanguage.uiLang(pref, deviceLanguageTag())
        themeMode = Appearance.theme(theme)
        colorMode = Appearance.color(color)
    }
}
