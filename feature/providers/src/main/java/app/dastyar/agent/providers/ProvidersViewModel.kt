package app.dastyar.agent.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dastyar.agent.data.ProviderEntity
import app.dastyar.agent.data.ProviderRepository
import app.dastyar.agent.data.SettingsStore
import app.dastyar.agent.network.LlmClientFactory
import app.dastyar.agent.network.LlmOptions
import app.dastyar.agent.security.ScreenLockRequiredException
import app.dastyar.agent.security.UserAuthenticator
import app.dastyar.agent.security.VaultLockedException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KeyForm(
    val cardId: String,
    val editingId: String? = null,
    val label: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val keyDirty: Boolean = false,
    val keyHint: String = "",
    val accountId: String = "",
    val headers: String = "",
    val makeDefault: Boolean = true,
)

data class ProvidersUi(
    val lang: String = "fa",
    val freeTab: Boolean = true,
    val saved: List<ProviderEntity> = emptyList(),
    val form: KeyForm? = null,
    val testing: Boolean = false,
    val testMessage: String? = null,
    val notice: String? = null,
    val reveal: Boolean = false,
)

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val repo: ProviderRepository,
    private val factory: LlmClientFactory,
    private val settings: SettingsStore,
    private val authenticator: UserAuthenticator,
) : ViewModel() {
    private val _state = MutableStateFlow(ProvidersUi())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.data.collect { snap -> _state.update { it.copy(lang = snap.language) } }
        }
        viewModelScope.launch {
            repo.observe().collect { rows -> _state.update { it.copy(saved = rows) } }
        }
    }

    fun setFreeTab(free: Boolean) = _state.update { it.copy(freeTab = free, testMessage = null) }

    fun dismiss() = _state.update { it.copy(form = null, testMessage = null, reveal = false, notice = null) }

    fun openCard(card: ProviderCard) {
        if (card.discontinued || !card.needsKey) return
        _state.update {
            it.copy(
                form = KeyForm(
                    cardId = card.id,
                    label = card.name,
                    model = card.defaultModel,
                    baseUrl = card.defaultBase,
                    makeDefault = it.saved.isEmpty(),
                ),
                testMessage = null,
                reveal = false,
                notice = null,
            )
        }
    }

    fun edit(entity: ProviderEntity) {
        viewModelScope.launch {
            val profile = try {
                repo.profile(entity.id)
            } catch (_: VaultLockedException) {
                val lang = _state.value.lang
                val ok = authenticator.authenticate(if (lang.startsWith("fa")) "باز کردن کلید" else "Unlock key")
                if (!ok) {
                    _state.update { it.copy(notice = if (lang.startsWith("fa")) "قفل باز نشد." else "Unlock failed.") }
                    return@launch
                }
                repo.profile(entity.id)
            }
            val card = Catalog.cards.firstOrNull { it.kind == entity.kind } ?: Catalog.cards.first { it.id == "custom" }
            val hint = profile?.apiKey?.let { mask(it) }.orEmpty()
            _state.update {
                it.copy(
                    form = KeyForm(
                        cardId = card.id,
                        editingId = entity.id,
                        label = entity.label,
                        model = entity.model,
                        baseUrl = entity.baseUrl,
                        keyHint = hint,
                        accountId = profile?.accountId.orEmpty(),
                        headers = profile?.extraHeaders?.entries?.joinToString("\n") { (k, v) -> "$k: $v" }.orEmpty(),
                        makeDefault = entity.isDefault,
                    ),
                    reveal = false,
                    testMessage = null,
                )
            }
        }
    }

    fun update(block: (KeyForm) -> KeyForm) {
        _state.update { state -> state.copy(form = state.form?.let(block)) }
    }

    fun onKeyChange(value: String) = update { it.copy(apiKey = value, keyDirty = true) }

    suspend fun tryReveal(): Boolean {
        val gate = settings.current().biometricGate
        val lang = _state.value.lang
        if (gate) {
            val ok = authenticator.authenticate(if (lang.startsWith("fa")) "نمایش کلید" else "Reveal key")
            if (!ok) return false
        }
        val form = _state.value.form ?: return false
        val key = if (form.keyDirty) {
            form.apiKey
        } else {
            form.editingId?.let { repo.profile(it)?.apiKey }.orEmpty()
        }
        _state.update { it.copy(reveal = true, form = it.form?.copy(apiKey = key)) }
        return true
    }

    fun hideKey() = _state.update { state ->
        val form = state.form
        state.copy(
            reveal = false,
            form = if (form != null && !form.keyDirty) form.copy(apiKey = "") else form,
        )
    }

    fun test() {
        val form = _state.value.form ?: return
        val card = cardOf(form) ?: return
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testMessage = null) }
            val key = resolveKey(form)
            val options = LlmOptions(
                kind = card.kind,
                baseUrl = form.baseUrl.trim(),
                apiKey = key,
                model = form.model.trim(),
                accountId = form.accountId.trim().ifBlank { null },
                extraHeaders = parseHeaders(form.headers),
            )
            val result = try {
                factory.clientFor(card.kind).test(options)
            } catch (error: Exception) {
                _state.update {
                    it.copy(testing = false, testMessage = scrub(error.message ?: "error", key))
                }
                return@launch
            }
            val text = buildString {
                append(if (result.ok) "OK" else "FAIL")
                append(" · ")
                append(result.latencyMs)
                append(" ms")
                if (result.message.isNotBlank()) {
                    append("\n")
                    append(scrub(result.message, key))
                }
                if (result.suggestProxy) {
                    val lang = _state.value.lang
                    append("\n")
                    append(if (lang.startsWith("fa")) "اگر شبکه مسدود است، آدرس پایهٔ سفارشی بگذارید." else "If the network is blocked, set a custom base URL.")
                }
            }
            _state.update { it.copy(testing = false, testMessage = text) }
        }
    }

    fun save() {
        val form = _state.value.form ?: return
        val card = cardOf(form) ?: return
        val lang = _state.value.lang
        if (form.model.isBlank() || form.baseUrl.isBlank()) {
            _state.update {
                it.copy(notice = if (lang.startsWith("fa")) "مدل و آدرس پایه لازم است." else "Model and base URL are required.")
            }
            return
        }
        viewModelScope.launch {
            try {
                repo.save(
                    id = form.editingId,
                    kind = card.kind,
                    label = form.label.ifBlank { card.name },
                    model = form.model,
                    baseUrl = form.baseUrl,
                    apiKey = if (form.keyDirty) form.apiKey else null,
                    accountId = form.accountId.trim(),
                    extraHeaders = parseHeaders(form.headers),
                    makeDefault = form.makeDefault,
                )
                _state.update {
                    it.copy(
                        form = null,
                        reveal = false,
                        notice = if (lang.startsWith("fa")) "ذخیره شد." else "Saved.",
                    )
                }
            } catch (_: VaultLockedException) {
                _state.update { it.copy(notice = if (lang.startsWith("fa")) "مخزن قفل است." else "The vault is locked.") }
            } catch (_: ScreenLockRequiredException) {
                _state.update { it.copy(notice = if (lang.startsWith("fa")) "قفل صفحه لازم است." else "A screen lock is required.") }
            }
        }
    }

    fun setDefault(id: String) {
        viewModelScope.launch { repo.setDefault(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    private suspend fun resolveKey(form: KeyForm): String {
        if (form.keyDirty) return form.apiKey
        val id = form.editingId ?: return form.apiKey
        return repo.profile(id)?.apiKey.orEmpty()
    }

    private fun cardOf(form: KeyForm): ProviderCard? = Catalog.cards.firstOrNull { it.id == form.cardId }

    private fun parseHeaders(raw: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        raw.lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) return@forEach
            val name = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) out[name] = value
        }
        return out
    }

    private fun scrub(message: String, key: String): String {
        var text = message
        if (key.length >= 4) text = text.replace(key, "••••")
        return text.take(400)
    }

    private fun mask(key: String): String = if (key.length <= 4) "••••" else "••••${key.takeLast(4)}"
}
