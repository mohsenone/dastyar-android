package app.dastyar.agent.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dastyar.agent.data.AppLanguage
import app.dastyar.agent.data.Appearance
import app.dastyar.agent.data.AuditEntity
import app.dastyar.agent.data.AuditRepository
import app.dastyar.agent.data.ChatRepository
import app.dastyar.agent.data.LocaleGate
import app.dastyar.agent.data.MemoryEntity
import app.dastyar.agent.data.MemoryRepository
import app.dastyar.agent.data.ProviderRepository
import app.dastyar.agent.data.RoutineEntity
import app.dastyar.agent.data.RoutineRepository
import app.dastyar.agent.data.SettingsStore
import app.dastyar.agent.data.deviceLanguageTag
import app.dastyar.agent.loop.AgentLoop
import app.dastyar.agent.loop.ConfirmationBus
import app.dastyar.agent.security.ScreenLockRequiredException
import app.dastyar.agent.security.SecretVault
import app.dastyar.agent.tools.AutomationGate
import app.dastyar.agent.tools.RoutineSchedulerPort
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUi(
    val lang: String = LocaleGate.uiLang,
    val languagePref: String = LocaleGate.pref,
    val themeMode: String = LocaleGate.themeMode,
    val colorMode: String = LocaleGate.colorMode,
    val paused: Boolean = false,
    val biometric: Boolean = false,
    val hardware: Boolean = false,
    val allowlist: String = "",
    val saf: String? = null,
    val routines: List<RoutineEntity> = emptyList(),
    val memories: List<MemoryEntity> = emptyList(),
    val audit: List<AuditEntity> = emptyList(),
    val auditTool: String = "",
    val auditSuccess: Boolean? = null,
    val message: String? = null,
    val onboardingDone: Boolean = false,
    val a11yDisclosed: Boolean = false,
    val confirmMode: String = "sensitive",
    val disabledCategories: String = "",
    val disabledTools: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val vault: SecretVault,
    private val routines: RoutineRepository,
    private val scheduler: RoutineSchedulerPort,
    private val memories: MemoryRepository,
    private val audit: AuditRepository,
    private val chats: ChatRepository,
    private val providers: ProviderRepository,
    private val loop: AgentLoop,
    private val confirm: ConfirmationBus,
) : ViewModel() {
    private val filter = MutableStateFlow("" to null as Boolean?)
    private val _state = MutableStateFlow(SettingsUi())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.data.collect { snap ->
                _state.update {
                    it.copy(
                        lang = AppLanguage.uiLang(snap.language, deviceLanguageTag()),
                        languagePref = snap.language,
                        themeMode = snap.themeMode,
                        colorMode = snap.colorMode,
                        paused = snap.toolsPaused,
                        biometric = snap.biometricGate,
                        allowlist = if (it.allowlist == snap.webAllowlist || it.allowlist.isEmpty()) snap.webAllowlist else it.allowlist,
                        saf = snap.safTreeUri,
                        onboardingDone = snap.onboardingDone,
                        a11yDisclosed = snap.a11yDisclosed,
                        confirmMode = snap.confirmMode,
                        disabledCategories = snap.disabledCategories,
                        disabledTools = if (it.disabledTools == snap.disabledTools || it.disabledTools.isEmpty()) snap.disabledTools else it.disabledTools,
                        hardware = runCatching { vault.hardwareBacked() }.getOrDefault(false),
                    )
                }
                AutomationGate.paused = snap.toolsPaused
            }
        }
        viewModelScope.launch {
            routines.observe().collect { rows -> _state.update { it.copy(routines = rows) } }
        }
        viewModelScope.launch {
            memories.observe().collect { rows -> _state.update { it.copy(memories = rows) } }
        }
        viewModelScope.launch {
            filter.flatMapLatest { (tool, success) -> audit.observe(tool, success) }
                .collect { rows -> _state.update { it.copy(audit = rows) } }
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { settings.setLanguage(lang) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setColorMode(mode: String) {
        viewModelScope.launch { settings.setColorMode(mode) }
    }

    fun finishOnboarding() {
        viewModelScope.launch { settings.setOnboardingDone(true) }
    }

    fun setPaused(paused: Boolean) {
        AutomationGate.paused = paused
        if (paused) {
            loop.cancel()
            confirm.denyAll()
        }
        viewModelScope.launch { settings.setToolsPaused(paused) }
    }

    fun setBiometric(enabled: Boolean) {
        viewModelScope.launch {
            try {
                vault.setUserAuthRequired(enabled)
                settings.setBiometricGate(enabled)
                _state.update { it.copy(message = null) }
            } catch (_: ScreenLockRequiredException) {
                val lang = _state.value.lang
                _state.update {
                    it.copy(message = if (lang.startsWith("fa")) "اول قفل صفحه بگذارید." else "Set a screen lock first.")
                }
            }
        }
    }

    fun setConfirmMode(mode: String) {
        viewModelScope.launch { settings.setConfirmMode(mode) }
    }

    fun setCategoryEnabled(category: String, enabled: Boolean) {
        val current = _state.value.disabledCategories.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        if (enabled) current.remove(category) else current.add(category)
        val joined = current.joinToString(",")
        _state.update { it.copy(disabledCategories = joined) }
        viewModelScope.launch { settings.setDisabledCategories(joined) }
    }

    fun setDisabledTools(value: String) {
        _state.update { it.copy(disabledTools = value) }
        viewModelScope.launch { settings.setDisabledTools(value) }
    }

    fun setAllowlist(value: String) {
        _state.update { it.copy(allowlist = value) }
        viewModelScope.launch { settings.setWebAllowlist(value) }
    }

    fun setSaf(uri: String?) {
        viewModelScope.launch { settings.setSafTree(uri) }
    }

    fun markA11yDisclosed() {
        viewModelScope.launch { settings.setA11yDisclosed(true) }
    }

    fun addMemory(fact: String) {
        if (fact.isBlank()) return
        viewModelScope.launch { memories.add(fact) }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { memories.delete(id) }
    }

    fun setAuditTool(value: String) {
        _state.update { it.copy(auditTool = value) }
        filter.value = value to _state.value.auditSuccess
    }

    fun setAuditSuccess(value: Boolean?) {
        _state.update { it.copy(auditSuccess = value) }
        filter.value = _state.value.auditTool to value
    }

    suspend fun exportAudit(): String = audit.exportText()

    fun deleteAudit() {
        viewModelScope.launch { audit.deleteAll() }
    }

    fun deleteAllKeys() {
        viewModelScope.launch {
            providers.deleteAllSecrets()
            val lang = _state.value.lang
            _state.update { it.copy(message = if (lang.startsWith("fa")) "همهٔ کلیدها پاک شد." else "All keys deleted.") }
        }
    }

    fun deleteAllChats() {
        viewModelScope.launch { chats.deleteAll() }
    }

    fun addRoutine(
        title: String,
        prompt: String,
        hour: Int,
        minute: Int,
        daily: Boolean,
        unattended: Boolean,
        triggerKind: String = "schedule",
        triggerFilter: String = "",
    ) {
        if (prompt.isBlank()) return
        val kind = if (triggerKind in setOf("schedule", "sms", "notification")) triggerKind else "schedule"
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            routines.save(
                RoutineEntity(
                    id = id,
                    title = title.ifBlank { if (_state.value.lang.startsWith("fa")) "روال" else "Routine" },
                    prompt = prompt.trim(),
                    hour = hour.coerceIn(0, 23),
                    minute = minute.coerceIn(0, 59),
                    repeatDaily = daily,
                    enabled = true,
                    allowUnattended = unattended,
                    createdAt = System.currentTimeMillis(),
                    triggerKind = kind,
                    triggerFilter = triggerFilter.trim().take(120),
                ),
            )
            scheduler.schedule(id)
        }
    }

    fun setRoutineEnabled(routine: RoutineEntity, enabled: Boolean) {
        viewModelScope.launch {
            routines.save(routine.copy(enabled = enabled))
            if (enabled) scheduler.schedule(routine.id) else scheduler.cancel(routine.id)
        }
    }

    fun deleteRoutine(routine: RoutineEntity) {
        viewModelScope.launch {
            scheduler.cancel(routine.id)
            routines.delete(routine.id)
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun packageLabel(): String = context.packageName
}
