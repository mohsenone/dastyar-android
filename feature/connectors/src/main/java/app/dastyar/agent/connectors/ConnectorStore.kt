package app.dastyar.agent.connectors

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class McpServer(
    val id: String,
    val name: String,
    val url: String,
    val transport: String,
)

data class ConnectorSnapshot(
    val enabled: Set<String> = emptySet(),
    val googleClientId: String = "",
    val googleRedirect: String = OAuthRedirect.DEFAULT,
    val googleExtraScopes: Boolean = false,
    val mcp: List<McpServer> = emptyList(),
    val rss: List<String> = emptyList(),
    val searchKind: String = "tavily",
    val searxngBase: String = "",
    val weatherKind: String = "open-meteo",
    val webhookUrl: String = "",
    val webhookHeader: String = "Authorization",
    val notes: Map<String, String> = emptyMap(),
)

object VaultIds {
    const val GOOGLE_TOKENS = "connector:google:tokens"
    const val GOOGLE_SECRET = "connector:google:secret"
    const val GITHUB = "connector:github:token"
    const val TELEGRAM = "connector:telegram:token"
    const val NOTION = "connector:notion:token"
    const val TAVILY = "connector:tavily:token"
    const val BRAVE = "connector:brave:token"
    const val OPENWEATHER = "connector:openweather:token"
    const val WEBHOOK = "connector:webhook:secret"
    fun mcp(id: String) = "connector:mcp:$id"
}

@Singleton
class ConnectorStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("dastyar_connectors") },
    )
    private val cache = AtomicReference(ConnectorSnapshot())
    private val readable = AtomicBoolean(true)
    private val corrupt = AtomicBoolean(false)
    val flow: Flow<ConnectorSnapshot> = store.data
        .catch {
            corrupt.set(true)
            readable.set(false)
            emit(emptyPreferences())
        }
        .map { prefs ->
            if (corrupt.get()) {
                cache.get()
            } else {
                val parsed = ConnectorDocs.read(prefs[KEY])
                readable.set(parsed.recognized)
                if (parsed.recognized) parsed.snapshot.also { cache.set(it) } else cache.get()
            }
        }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { flow.collect { } }
        }
    }

    /** False when the saved document is not a connector object. The file is left as it is. */
    fun canWrite(): Boolean = readable.get()

    fun cached(): ConnectorSnapshot = cache.get()

    suspend fun current(): ConnectorSnapshot = runCatching { flow.first() }.getOrDefault(cache.get())

    suspend fun setEnabled(id: String, enabled: Boolean) = update { snap ->
        snap.copy(enabled = if (enabled) snap.enabled + id else snap.enabled - id)
    }

    suspend fun setGoogle(clientId: String, redirect: String, extraScopes: Boolean) = update {
        it.copy(googleClientId = clientId.trim(), googleRedirect = redirect.trim().ifBlank { OAuthRedirect.DEFAULT }, googleExtraScopes = extraScopes)
    }

    suspend fun setSearch(kind: String, base: String) = update { it.copy(searchKind = kind, searxngBase = base.trim()) }

    suspend fun setWeather(kind: String) = update { it.copy(weatherKind = kind) }

    suspend fun setWebhook(url: String, header: String) = update {
        it.copy(webhookUrl = url.trim(), webhookHeader = header.trim().ifBlank { "Authorization" })
    }

    suspend fun setRss(urls: List<String>) = update { it.copy(rss = urls.mapNotNull(RssUrls::normalize).distinct()) }

    suspend fun upsertMcp(server: McpServer) = update { snap ->
        snap.copy(mcp = snap.mcp.filterNot { it.id == server.id } + server)
    }

    suspend fun deleteMcp(id: String) = update { it.copy(mcp = it.mcp.filterNot { server -> server.id == id }) }

    suspend fun setNote(id: String, note: String) = update { snap ->
        val next = snap.notes.toMutableMap()
        if (note.isBlank()) next.remove(id) else next[id] = note.take(300)
        snap.copy(notes = next)
    }

    private suspend fun update(block: (ConnectorSnapshot) -> ConnectorSnapshot) {
        runCatching {
            store.edit { prefs ->
                val parsed = ConnectorDocs.read(prefs[KEY])
                readable.set(parsed.recognized)
                if (!parsed.recognized) return@edit
                val next = block(parsed.snapshot)
                prefs[KEY] = ConnectorDocs.write(next)
                cache.set(next)
            }
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("doc")
    }
}
