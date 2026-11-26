package app.dastyar.agent.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {
    @Test
    fun missingLanguageIsEnglishAndStoredPersianStays() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.normalize(null))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.normalize(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.normalize("  "))
        assertEquals(AppLanguage.PERSIAN, AppLanguage.normalize("fa"))
        assertEquals(AppLanguage.PERSIAN, AppLanguage.normalize("fa-IR"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.normalize("en"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.normalize("system"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.normalize("de"))
    }

    @Test
    fun localeTagForcesEnglishOrPersianAndLeavesSystemEmpty() {
        assertNull(AppLanguage.localeTag("system"))
        assertEquals("en", AppLanguage.localeTag(null))
        assertEquals("en", AppLanguage.localeTag("en"))
        assertEquals("fa", AppLanguage.localeTag("fa"))
    }

    @Test
    fun uiLanguageFollowsDeviceOnlyForSystem() {
        assertEquals("fa", AppLanguage.uiLang("fa", "en"))
        assertEquals("en", AppLanguage.uiLang("en", "fa"))
        assertEquals("en", AppLanguage.uiLang(null, "fa"))
        assertEquals("fa", AppLanguage.uiLang("system", "fa"))
        assertEquals("fa", AppLanguage.uiLang("system", "fa-IR"))
        assertEquals("en", AppLanguage.uiLang("system", "en"))
        assertEquals("en", AppLanguage.uiLang("system", "de"))
    }

    @Test
    fun preferenceMappingKeepsStoredPersianAndDefaultsTheRest() {
        val fresh = emptyPreferences().toSettingsSnapshot()
        assertEquals(AppLanguage.ENGLISH, fresh.language)
        assertEquals(Appearance.SYSTEM, fresh.themeMode)
        assertEquals(Appearance.BRAND, fresh.colorMode)

        val persian = mutablePreferencesOf(stringPreferencesKey("language") to "fa").toSettingsSnapshot()
        assertEquals(AppLanguage.PERSIAN, persian.language)

        val systemDark = mutablePreferencesOf(
            stringPreferencesKey("language") to "system",
            stringPreferencesKey("theme_mode") to "dark",
            stringPreferencesKey("color_mode") to "dynamic",
            booleanPreferencesKey("onboarding_done") to true,
        ).toSettingsSnapshot()
        assertEquals(AppLanguage.SYSTEM, systemDark.language)
        assertEquals(Appearance.DARK, systemDark.themeMode)
        assertEquals(Appearance.DYNAMIC, systemDark.colorMode)
        assertEquals(true, systemDark.onboardingDone)
    }
}
