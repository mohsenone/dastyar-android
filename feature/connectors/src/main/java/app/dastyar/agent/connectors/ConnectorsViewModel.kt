package app.dastyar.agent.connectors

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dastyar.agent.data.AppLanguage
import app.dastyar.agent.data.LocaleGate
import app.dastyar.agent.data.deviceLanguageTag
import app.dastyar.agent.security.SecretVault
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectorRow(
    val id: String,
    val status: LinkStatus,
    val note: String,
    val broken: Boolean = false,
)

data class ConnectorsUi(
    val lang: String = LocaleGate.uiLang,
    val rows: List<ConnectorRow> = emptyList(),
    val snap: ConnectorSnapshot = ConnectorSnapshot(),
    val secrets: Set<String> = emptySet(),
    val message: String? = null,
    val busy: Boolean = false,
    val loadError: Boolean = false,
    val unreadable: Boolean = false,
)

@HiltViewModel
class ConnectorsViewModel @Inject constructor(
    private val store: ConnectorStore,
    private val vault: SecretVault,
    private val runtime: ConnectorRuntime,
    private val auth: GoogleAuth,
    private val tools: ConnectorToolSource,
    private val settings: app.dastyar.agent.data.SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectorsUi())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                settings.data.collect { snap ->
                    _state.update { it.copy(lang = AppLanguage.uiLang(snap.language, deviceLanguageTag())) }
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                store.flow.collect { refreshRows() }
            }.onFailure {
                val rows = ConnectorIds.catalog.map { ConnectorRow(it, LinkStatus.ERROR, "", broken = true) }
                _state.update { state -> state.copy(rows = rows, loadError = true) }
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            store.setEnabled(id, enabled)
            if (id == ConnectorIds.MCP) tools.reloadMcp()
            if (id == ConnectorIds.SHIZUKU) {
            store.setNote(id, runCatching { ShizukuBridge.status() }.getOrDefault("error"))
        }
        }
    }

    fun saveSecret(id: String, value: String) {
        if (value.isBlank()) return
        viewModelScope.launch {
            val saved = runCatching { vault.put(secretId(id), value.trim()) }.isSuccess
            _state.update {
                it.copy(
                    message = when {
                        !saved && it.lang.startsWith("fa") -> "رمز ذخیره نشد."
                        !saved -> "Could not store the secret."
                        it.lang.startsWith("fa") -> "رمز در Keystore ذخیره شد."
                        else -> "Secret saved in the Keystore."
                    },
                )
            }
            refreshRows()
        }
    }

    fun clearSecret(id: String) {
        viewModelScope.launch {
            vault.delete(secretId(id))
            if (id == ConnectorIds.GOOGLE) vault.delete(VaultIds.GOOGLE_TOKENS)
            refreshRows()
        }
    }

    fun saveGoogle(clientId: String, redirect: String, extra: Boolean) {
        val problem = OAuthRedirect.problem(redirect.ifBlank { OAuthRedirect.DEFAULT })
        if (problem != null) {
            _state.update { it.copy(message = problem) }
            return
        }
        viewModelScope.launch { store.setGoogle(clientId, redirect.ifBlank { OAuthRedirect.DEFAULT }, extra) }
    }

    suspend fun prepareGoogle(clientId: String, redirect: String, secret: String, extra: Boolean): Intent? {
        val target = redirect.ifBlank { OAuthRedirect.DEFAULT }
        val problem = OAuthRedirect.problem(target)
        if (clientId.isBlank() || problem != null) {
            _state.update { it.copy(message = problem ?: if (_state.value.lang.startsWith("fa")) "شناسهٔ کلاینت لازم است." else "A client id is required.") }
            return null
        }
        if (!store.canWrite()) {
            _state.update { it.copy(unreadable = true) }
            return null
        }
        store.setGoogle(clientId, target, extra)
        if (secret.isNotBlank()) runCatching { vault.put(VaultIds.GOOGLE_SECRET, secret.trim()) }
        val intent = auth.intent(clientId.trim(), target, extra)
        if (intent == null) {
            _state.update {
                it.copy(message = if (it.lang.startsWith("fa")) "ورود گوگل شروع نشد. یک مرورگر نصب باشد." else "Google sign-in could not start. Check that a browser is installed.")
            }
        }
        return intent
    }

    fun onGoogleActivity(data: Intent?) {
        viewModelScope.launch {
            val note = auth.consume(data)
            store.setNote(ConnectorIds.GOOGLE, note)
            _state.update { it.copy(message = note.removePrefix("error: ").trim()) }
            refreshRows()
        }
    }

    fun saveSearch(kind: String, base: String) {
        viewModelScope.launch { store.setSearch(kind, base) }
    }

    fun saveWeather(kind: String) {
        viewModelScope.launch { store.setWeather(kind) }
    }

    fun saveWebhook(url: String, header: String) {
        viewModelScope.launch { store.setWebhook(url, header) }
    }

    fun saveRss(urls: String) {
        viewModelScope.launch { store.setRss(urls.lines()) }
    }

    fun addMcp(name: String, url: String, transport: String, token: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            val id = McpNames.slug(name.ifBlank { url }).take(24).ifBlank { UUID.randomUUID().toString().take(8) }
            val unique = if (store.current().mcp.any { it.id == id }) "$id-${UUID.randomUUID().toString().take(4)}" else id
            store.upsertMcp(McpServer(unique, name.ifBlank { unique }, url.trim(), transport.ifBlank { "auto" }))
            if (token.isNotBlank()) vault.put(VaultIds.mcp(unique), token.trim())
            store.setEnabled(ConnectorIds.MCP, true)
            tools.reloadMcp()
        }
    }

    fun deleteMcp(id: String) {
        viewModelScope.launch {
            store.deleteMcp(id)
            vault.delete(VaultIds.mcp(id))
            tools.reloadMcp()
        }
    }

    fun test(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val note = runtime.test(id)
            if (id == ConnectorIds.MCP) tools.reloadMcp()
            _state.update { it.copy(busy = false, message = note.removePrefix("error: ").removePrefix("connected").ifBlank { if (it.lang.startsWith("fa")) "متصل شد." else "Connected." }.trim()) }
            refreshRows()
        }
    }

    fun requestShizuku() {
        runCatching { ShizukuBridge.requestPermission() }
        viewModelScope.launch {
            store.setNote(ConnectorIds.SHIZUKU, runCatching { ShizukuBridge.status() }.getOrDefault("error"))
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun retry() {
        viewModelScope.launch {
            _state.update { it.copy(loadError = false) }
            refreshRows()
        }
    }

    private suspend fun refreshRows() {
        try {
            val snap = store.current()
            val unreadable = !store.canWrite()
            var vaultFailed = false
            suspend fun has(id: String): Boolean = try {
                !vault.get(id).isNullOrBlank()
            } catch (_: Throwable) {
                vaultFailed = true
                false
            }
            val secrets = buildSet {
                if (has(VaultIds.GOOGLE_TOKENS)) add(ConnectorIds.GOOGLE)
                if (has(VaultIds.GITHUB)) add(ConnectorIds.GITHUB)
                if (has(VaultIds.TELEGRAM)) add(ConnectorIds.TELEGRAM)
                if (has(VaultIds.NOTION)) add(ConnectorIds.NOTION)
                if (has(VaultIds.TAVILY) || has(VaultIds.BRAVE) || snap.searxngBase.isNotBlank()) add(ConnectorIds.SEARCH)
                if (has(VaultIds.OPENWEATHER)) add(ConnectorIds.WEATHER)
                if (snap.mcp.isNotEmpty()) add(ConnectorIds.MCP)
            }
            val shizukuNote = runCatching { ShizukuBridge.status() }.getOrDefault("error")
            val rows = ConnectorIds.catalog.map { id ->
                runCatching {
                    val note = if (id == ConnectorIds.SHIZUKU) shizukuNote else snap.notes[id].orEmpty()
                    ConnectorRow(
                        id = id,
                        status = ConnectorStatus.of(
                            id = id,
                            enabled = id in snap.enabled,
                            hasCredential = id in secrets,
                            note = note,
                            weatherKind = snap.weatherKind,
                            webhookUrl = snap.webhookUrl,
                            rssCount = snap.rss.size,
                            mcpReady = id == ConnectorIds.MCP && note == "connected",
                        ),
                        note = note,
                    )
                }.getOrElse { ConnectorRow(id, LinkStatus.ERROR, "", broken = true) }
            }
            _state.update {
                it.copy(
                    snap = snap,
                    secrets = secrets,
                    rows = rows,
                    loadError = false,
                    unreadable = unreadable || vaultFailed,
                )
            }
        } catch (_: Throwable) {
            val rows = ConnectorIds.catalog.map { ConnectorRow(it, LinkStatus.ERROR, "", broken = true) }
            _state.update { it.copy(rows = rows, loadError = true) }
        }
    }

    private fun secretId(id: String) = when (id) {
        ConnectorIds.GOOGLE -> VaultIds.GOOGLE_SECRET
        ConnectorIds.GITHUB -> VaultIds.GITHUB
        ConnectorIds.TELEGRAM -> VaultIds.TELEGRAM
        ConnectorIds.NOTION -> VaultIds.NOTION
        "tavily" -> VaultIds.TAVILY
        "brave" -> VaultIds.BRAVE
        "openweather" -> VaultIds.OPENWEATHER
        ConnectorIds.WEBHOOK -> VaultIds.WEBHOOK
        else -> "connector:$id:token"
    }
}
