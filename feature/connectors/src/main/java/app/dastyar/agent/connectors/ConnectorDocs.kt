package app.dastyar.agent.connectors

import org.json.JSONArray
import org.json.JSONObject

/**
 * Result of reading the connectors DataStore document.
 * [recognized] is false when the bytes are not a connector object.
 * Callers must keep the stored text in that case so an upgrade does not wipe it.
 */
data class ConnectorDocument(
    val snapshot: ConnectorSnapshot = ConnectorSnapshot(),
    val recognized: Boolean = true,
)

/** Pure parser and writer for `dastyar_connectors`. Never throws. */
object ConnectorDocs {
    fun read(raw: String?): ConnectorDocument {
        if (raw.isNullOrBlank()) return ConnectorDocument()
        return try {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) return ConnectorDocument(recognized = false)
            if (!trimmed.startsWith("{")) return ConnectorDocument(recognized = false)
            ConnectorDocument(snapshot = objectSnapshot(JSONObject(trimmed)), recognized = true)
        } catch (_: Throwable) {
            ConnectorDocument(recognized = false)
        }
    }

    fun write(snap: ConnectorSnapshot): String {
        val notes = JSONObject()
        snap.notes.forEach { (key, value) -> notes.put(key, value) }
        return JSONObject()
            .put("enabled", JSONArray(snap.enabled.toList()))
            .put("googleClientId", snap.googleClientId)
            .put("googleRedirect", snap.googleRedirect)
            .put("googleExtraScopes", snap.googleExtraScopes)
            .put("mcp", JSONArray().apply {
                snap.mcp.forEach {
                    put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("transport", it.transport))
                }
            })
            .put("rss", JSONArray(snap.rss))
            .put("searchKind", snap.searchKind)
            .put("searxngBase", snap.searxngBase)
            .put("weatherKind", snap.weatherKind)
            .put("webhookUrl", snap.webhookUrl)
            .put("webhookHeader", snap.webhookHeader)
            .put("notes", notes)
            .toString()
    }

    private fun objectSnapshot(obj: JSONObject): ConnectorSnapshot {
        val mcp = buildList {
            val value = obj.opt("mcp")
            val array = value as? JSONArray ?: return@buildList
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val id = text(row, "id")
                if (id.isBlank()) continue
                add(
                    McpServer(
                        id = id,
                        name = text(row, "name").ifBlank { id },
                        url = text(row, "url"),
                        transport = text(row, "transport", "auto").ifBlank { "auto" },
                    ),
                )
            }
        }
        val notes = buildMap {
            val value = obj.opt("notes")
            val o = value as? JSONObject ?: return@buildMap
            val keys = o.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, text(o, key))
            }
        }
        return ConnectorSnapshot(
            enabled = enabled(obj),
            googleClientId = text(obj, "googleClientId"),
            googleRedirect = text(obj, "googleRedirect").ifBlank { OAuthRedirect.DEFAULT },
            googleExtraScopes = bool(obj, "googleExtraScopes"),
            mcp = mcp,
            rss = stringList(obj.opt("rss") as? JSONArray).filter { it.isNotBlank() },
            searchKind = text(obj, "searchKind", "tavily").ifBlank { "tavily" },
            searxngBase = text(obj, "searxngBase"),
            weatherKind = text(obj, "weatherKind", "open-meteo").ifBlank { "open-meteo" },
            webhookUrl = text(obj, "webhookUrl"),
            webhookHeader = text(obj, "webhookHeader").ifBlank { "Authorization" },
            notes = notes,
        )
    }

    private fun enabled(obj: JSONObject): Set<String> {
        if (!obj.has("enabled") || obj.isNull("enabled")) return emptySet()
        return when (val value = obj.get("enabled")) {
            is JSONArray -> stringList(value).toSet()
            is JSONObject -> buildSet {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.isNotBlank() && bool(value, key)) add(key)
                }
            }
            is String -> value.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            else -> emptySet()
        }
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                when (val value = runCatching { array.get(i) }.getOrNull()) {
                    is String -> if (value.isNotBlank()) add(value)
                    is Number -> add(value.toString())
                    else -> Unit
                }
            }
        }
    }

    private fun text(obj: JSONObject, key: String, fallback: String = ""): String {
        if (!obj.has(key) || obj.isNull(key)) return fallback
        return when (val value = runCatching { obj.get(key) }.getOrNull()) {
            is String -> value
            is Number, is Boolean -> value.toString()
            else -> fallback
        }
    }

    private fun bool(obj: JSONObject, key: String): Boolean {
        if (!obj.has(key) || obj.isNull(key)) return false
        return when (val value = runCatching { obj.get(key) }.getOrNull()) {
            is Boolean -> value
            is String -> value.equals("true", true) || value == "1"
            is Number -> value.toInt() != 0
            else -> false
        }
    }
}
