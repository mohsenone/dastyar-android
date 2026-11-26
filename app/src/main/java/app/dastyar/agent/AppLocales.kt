package app.dastyar.agent

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.dastyar.agent.data.AppLanguage

internal object AppLocales {
    fun apply(pref: String) {
        val tag = AppLanguage.localeTag(pref)
        val locales = if (tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
