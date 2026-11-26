package app.dastyar.agent.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class SettingsSnapshot(
    val onboardingDone: Boolean = false,
    val language: String = "fa",
    val toolsPaused: Boolean = false,
    val biometricGate: Boolean = false,
    val safTreeUri: String? = null,
    val webAllowlist: String = "",
    val activeConversationId: String? = null,
    val a11yDisclosed: Boolean = false,
    /** always, sensitive, or auto. Default is sensitive. */
    val confirmMode: String = "sensitive",
    val disabledCategories: String = "",
    val disabledTools: String = "",
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("dastyar_settings") },
    )

    val data: Flow<SettingsSnapshot> = store.data.map { it.toSnapshot() }

    suspend fun current(): SettingsSnapshot = data.first()

    suspend fun setOnboardingDone(done: Boolean) = edit { it[ONBOARDING] = done }

    suspend fun setLanguage(language: String) = edit { it[LANGUAGE] = language }

    suspend fun setToolsPaused(paused: Boolean) = edit { it[PAUSED] = paused }

    suspend fun setBiometricGate(enabled: Boolean) = edit { it[BIOMETRIC] = enabled }

    suspend fun setSafTree(uri: String?) = edit {
        if (uri == null) it.remove(SAF) else it[SAF] = uri
    }

    suspend fun setWebAllowlist(value: String) = edit { it[ALLOWLIST] = value }

    suspend fun setActiveConversation(id: String?) = edit {
        if (id == null) it.remove(ACTIVE) else it[ACTIVE] = id
    }

    suspend fun setA11yDisclosed(done: Boolean) = edit { it[A11Y] = done }

    suspend fun setConfirmMode(mode: String) = edit { it[CONFIRM] = mode }

    suspend fun setDisabledCategories(value: String) = edit { it[CATEGORIES] = value }

    suspend fun setDisabledTools(value: String) = edit { it[TOOLS] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit(block)
    }

    private fun Preferences.toSnapshot() = SettingsSnapshot(
        onboardingDone = this[ONBOARDING] ?: false,
        language = this[LANGUAGE] ?: "fa",
        toolsPaused = this[PAUSED] ?: false,
        biometricGate = this[BIOMETRIC] ?: false,
        safTreeUri = this[SAF],
        webAllowlist = this[ALLOWLIST] ?: "",
        activeConversationId = this[ACTIVE],
        a11yDisclosed = this[A11Y] ?: false,
        confirmMode = this[CONFIRM] ?: "sensitive",
        disabledCategories = this[CATEGORIES] ?: "",
        disabledTools = this[TOOLS] ?: "",
    )

    private companion object {
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val LANGUAGE = stringPreferencesKey("language")
        val PAUSED = booleanPreferencesKey("tools_paused")
        val BIOMETRIC = booleanPreferencesKey("biometric_gate")
        val SAF = stringPreferencesKey("saf_tree")
        val ALLOWLIST = stringPreferencesKey("web_allowlist")
        val ACTIVE = stringPreferencesKey("active_conversation")
        val A11Y = booleanPreferencesKey("a11y_disclosed")
        val CONFIRM = stringPreferencesKey("confirm_mode")
        val CATEGORIES = stringPreferencesKey("disabled_categories")
        val TOOLS = stringPreferencesKey("disabled_tools")
    }
}
