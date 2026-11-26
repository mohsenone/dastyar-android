package app.dastyar.agent.connectors

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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
    val flow: Flow<ConnectorSnapshot> = store.data.map { parse(it[KEY]).also(cache::set) }

    init {
        CoroutineScope(Dispatchers.IO).launch { flow.collect { } }
    }

    fun cached(): ConnectorSnapshot = cache.get()

    suspend fun current(): ConnectorSnapshot = flow.first()

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
        store.edit { prefs ->
            val next = block(parse(prefs[KEY]))
            prefs[KEY] = write(next)
            cache.set(next)
        }
    }

    private fun parse(raw: String?): ConnectorSnapshot {
        if (raw.isNullOrBlank()) return ConnectorSnapshot()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return ConnectorSnapshot()
        val enabled = buildSet {
            val array = obj.optJSONArray("enabled") ?: JSONArray()
            for (i in 0 until array.length()) add(array.optString(i))
        }
        val mcp = buildList {
            val array = obj.optJSONArray("mcp") ?: JSONArray()
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val id = row.optString("id")
                if (id.isBlank()) continue
                add(McpServer(id, row.optString("name").ifBlank { id }, row.optString("url"), row.optString("transport", "auto")))
            }
        }
        val rss = buildList {
            val array = obj.optJSONArray("rss") ?: JSONArray()
            for (i in 0 until array.length()) add(array.optString(i))
        }.filter { it.isNotBlank() }
        val notes = buildMap {
            val o = obj.optJSONObject("notes") ?: JSONObject()
            o.keys().forEach { key -> put(key, o.optString(key)) }
        }
        return ConnectorSnapshot(
            enabled = enabled,
            googleClientId = obj.optString("googleClientId"),
            googleRedirect = obj.optString("googleRedirect").ifBlank { OAuthRedirect.DEFAULT },
            googleExtraScopes = obj.optBoolean("googleExtraScopes"),
            mcp = mcp,
            rss = rss,
            searchKind = obj.optString("searchKind", "tavily"),
            searxngBase = obj.optString("searxngBase"),
            weatherKind = obj.optString("weatherKind", "open-meteo"),
            webhookUrl = obj.optString("webhookUrl"),
            webhookHeader = obj.optString("webhookHeader").ifBlank { "Authorization" },
            notes = notes,
        )
    }

    private fun write(snap: ConnectorSnapshot): String {
        val notes = JSONObject()
        snap.notes.forEach { (key, value) -> notes.put(key, value) }
        return JSONObject()
            .put("enabled", JSONArray(snap.enabled.toList()))
            .put("googleClientId", snap.googleClientId)
            .put("googleRedirect", snap.googleRedirect)
            .put("googleExtraScopes", snap.googleExtraScopes)
            .put("mcp", JSONArray().apply { snap.mcp.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("transport", it.transport)) } })
            .put("rss", JSONArray(snap.rss))
            .put("searchKind", snap.searchKind)
            .put("searxngBase", snap.searxngBase)
            .put("weatherKind", snap.weatherKind)
            .put("webhookUrl", snap.webhookUrl)
            .put("webhookHeader", snap.webhookHeader)
            .put("notes", notes)
            .toString()
    }

    private companion object {
        val KEY = stringPreferencesKey("doc")
    }
}
