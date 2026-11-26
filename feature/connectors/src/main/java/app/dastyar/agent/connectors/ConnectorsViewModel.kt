package app.dastyar.agent.connectors

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

data class ConnectorsUi(
    val lang: String = "fa",
    val rows: List<ConnectorRow> = emptyList(),
    val snap: ConnectorSnapshot = ConnectorSnapshot(),
    val secrets: Set<String> = emptySet(),
    val message: String? = null,
    val busy: Boolean = false,
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
            settings.data.collect { snap -> _state.update { it.copy(lang = snap.language) } }
        }
        viewModelScope.launch {
            store.flow.collect {
                refreshRows()
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            store.setEnabled(id, enabled)
            if (id == ConnectorIds.MCP) tools.reloadMcp()
            if (id == ConnectorIds.SHIZUKU) store.setNote(id, ShizukuBridge.status())
        }
    }

    fun saveSecret(id: String, value: String) {
        if (value.isBlank()) return
        viewModelScope.launch {
            vault.put(secretId(id), value.trim())
            _state.update { it.copy(message = if (it.lang.startsWith("fa")) "رمز در Keystore ذخیره شد." else "Secret saved in the Keystore.") }
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
        store.setGoogle(clientId, target, extra)
        if (secret.isNotBlank()) vault.put(VaultIds.GOOGLE_SECRET, secret.trim())
        return auth.intent(clientId.trim(), target, extra)
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
        ShizukuBridge.requestPermission()
        viewModelScope.launch { store.setNote(ConnectorIds.SHIZUKU, ShizukuBridge.status()) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private suspend fun refreshRows() {
        val snap = store.current()
        val secrets = buildSet {
            if (!vault.get(VaultIds.GOOGLE_TOKENS).isNullOrBlank()) add(ConnectorIds.GOOGLE)
            if (!vault.get(VaultIds.GITHUB).isNullOrBlank()) add(ConnectorIds.GITHUB)
            if (!vault.get(VaultIds.TELEGRAM).isNullOrBlank()) add(ConnectorIds.TELEGRAM)
            if (!vault.get(VaultIds.NOTION).isNullOrBlank()) add(ConnectorIds.NOTION)
            if (!vault.get(VaultIds.TAVILY).isNullOrBlank() || !vault.get(VaultIds.BRAVE).isNullOrBlank() || snap.searxngBase.isNotBlank()) add(ConnectorIds.SEARCH)
            if (!vault.get(VaultIds.OPENWEATHER).isNullOrBlank()) add(ConnectorIds.WEATHER)
            if (snap.mcp.isNotEmpty()) add(ConnectorIds.MCP)
        }
        val shizukuNote = if (ConnectorIds.SHIZUKU in snap.enabled) ShizukuBridge.status() else snap.notes[ConnectorIds.SHIZUKU].orEmpty()
        val rows = listOf(
            ConnectorIds.GOOGLE, ConnectorIds.GITHUB, ConnectorIds.TELEGRAM, ConnectorIds.NOTION,
            ConnectorIds.WEBHOOK, ConnectorIds.SEARCH, ConnectorIds.RSS, ConnectorIds.WEATHER,
            ConnectorIds.MCP, ConnectorIds.SHIZUKU,
        ).map { id ->
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
                    mcpReady = note == "connected" || (id == ConnectorIds.MCP && note == "connected"),
                ),
                note = note,
            )
        }
        _state.update { it.copy(snap = snap, secrets = secrets, rows = rows) }
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
