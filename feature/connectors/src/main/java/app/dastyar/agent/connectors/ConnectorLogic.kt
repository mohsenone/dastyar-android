package app.dastyar.agent.connectors

import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

object ConnectorIds {
    const val GOOGLE = "google"
    const val GITHUB = "github"
    const val TELEGRAM = "telegram"
    const val NOTION = "notion"
    const val WEBHOOK = "webhook"
    const val SEARCH = "search"
    const val RSS = "rss"
    const val WEATHER = "weather"
    const val MCP = "mcp"
    const val SHIZUKU = "shizuku"

    val catalog = listOf(
        GOOGLE, GITHUB, TELEGRAM, NOTION, WEBHOOK, SEARCH, RSS, WEATHER, MCP, SHIZUKU,
    )
}

/** Null when the snapshot has not loaded or this catalog id is absent. Never throws. */
fun connectorRow(rows: List<ConnectorRow>, id: String): ConnectorRow? = rows.firstOrNull { it.id == id }

/** SHA-1 of one certificate. A missing certificate is a dash, never a throw. */
object SigningSha1 {
    fun format(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return "—"
        return runCatching {
            MessageDigest.getInstance("SHA-1").digest(bytes).joinToString(":") { "%02X".format(it) }
        }.getOrDefault("—")
    }
}

enum class LinkStatus { OFF, NEEDS_AUTH, CONNECTED, ERROR }

object McpNames {
    fun toolName(serverId: String, remoteName: String): String {
        val server = slug(serverId).take(18)
        val tool = slug(remoteName).take(36)
        return "mcp_${server}_$tool".take(64)
    }

    fun slug(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "x" }

    fun unique(base: String, used: MutableSet<String>): String {
        var name = base.take(64)
        var n = 2
        while (!used.add(name)) {
            name = (base.take(58) + "_$n").take(64)
            n++
        }
        return name
    }
}

object OAuthRedirect {
    const val DEFAULT = "app.dastyar.agent://oauth2redirect"

    /** Null when the URI matches the manifest intent-filter. */
    fun problem(uri: String): String? {
        val parsed = runCatching { URI(uri.trim()) }.getOrNull() ?: return "The redirect URI is not valid."
        if (!parsed.scheme.equals("app.dastyar.agent", true)) {
            return "The redirect scheme must be app.dastyar.agent so the app can receive it."
        }
        if (!parsed.host.equals("oauth2redirect", true)) {
            return "The redirect host must be oauth2redirect."
        }
        if (parsed.userInfo != null) return "The redirect URI must not contain user info."
        return null
    }
}

object WebhookPolicy {
    fun rejection(url: String, allowlist: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return "The URL is not valid."
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        if (host.isBlank()) return "The URL needs a host."
        val listed = hosts(allowlist)
        if (scheme == "http") {
            if (listed.none { matches(host, it) }) {
                return "Cleartext HTTP is allowed only when that host is on the allowlist."
            }
        } else if (scheme != "https") {
            return "Only https is allowed, or http for an allowlisted host."
        } else if (listed.isNotEmpty() && listed.none { matches(host, it) }) {
            return "That host is not on the allowlist."
        }
        return null
    }

    fun hosts(raw: String): List<String> =
        raw.split(',', ' ', '\n', '\t').map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.contains("://") }

    fun matches(host: String, rule: String): Boolean = host == rule || host.endsWith(".$rule")
}

object RssUrls {
    fun normalize(url: String): String? {
        val trimmed = url.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        if (uri.host.isNullOrBlank()) return null
        return trimmed
    }
}

object WeatherUrls {
    fun geocode(place: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${enc(place)}&count=1"

    fun forecast(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=3&timezone=auto"

    fun openWeather(place: String, key: String): String =
        "https://api.openweathermap.org/data/2.5/forecast?q=${enc(place)}&units=metric&cnt=8&appid=${enc(key)}"

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

object SsePayload {
    /** Pulls JSON data lines out of an SSE body. */
    fun jsonEvents(raw: String): List<String> {
        val events = mutableListOf<String>()
        val data = StringBuilder()
        fun flush() {
            val text = data.toString().trim()
            if (text.isNotEmpty() && text != "[DONE]") events += text
            data.clear()
        }
        raw.lineSequence().forEach { line ->
            if (line.isEmpty()) {
                flush()
            } else if (line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").trim())
            }
        }
        flush()
        return events
    }
}

object ConnectorStatus {
    fun of(
        id: String,
        enabled: Boolean,
        hasCredential: Boolean,
        note: String,
        weatherKind: String,
        webhookUrl: String,
        rssCount: Int,
        mcpReady: Boolean,
    ): LinkStatus {
        if (!enabled) return LinkStatus.OFF
        if (note.startsWith("error")) return LinkStatus.ERROR
        return when (id) {
            ConnectorIds.GOOGLE, ConnectorIds.GITHUB, ConnectorIds.TELEGRAM, ConnectorIds.NOTION ->
                if (hasCredential) LinkStatus.CONNECTED else LinkStatus.NEEDS_AUTH
            ConnectorIds.WEBHOOK -> when {
                webhookUrl.isBlank() -> LinkStatus.NEEDS_AUTH
                WebhookPolicy.rejection(webhookUrl, "") == null -> LinkStatus.CONNECTED
                webhookUrl.startsWith("http://") -> LinkStatus.NEEDS_AUTH
                else -> LinkStatus.ERROR
            }
            ConnectorIds.SEARCH -> if (hasCredential) LinkStatus.CONNECTED else LinkStatus.NEEDS_AUTH
            ConnectorIds.RSS -> if (rssCount > 0) LinkStatus.CONNECTED else LinkStatus.NEEDS_AUTH
            ConnectorIds.WEATHER -> if (weatherKind == "openweather" && !hasCredential) LinkStatus.NEEDS_AUTH else LinkStatus.CONNECTED
            ConnectorIds.MCP -> if (mcpReady) LinkStatus.CONNECTED else if (note.startsWith("error")) LinkStatus.ERROR else LinkStatus.NEEDS_AUTH
            ConnectorIds.SHIZUKU -> when (note) {
                "ready" -> LinkStatus.CONNECTED
                "missing", "" -> LinkStatus.NEEDS_AUTH
                else -> LinkStatus.ERROR
            }
            else -> LinkStatus.NEEDS_AUTH
        }
    }
}

object GoogleScopes {
    const val OPENID = "openid"
    const val EMAIL = "email"
    const val PROFILE = "profile"
    const val GMAIL_READ = "https://www.googleapis.com/auth/gmail.readonly"
    const val GMAIL_SEND = "https://www.googleapis.com/auth/gmail.send"
    const val CALENDAR = "https://www.googleapis.com/auth/calendar"
    const val DRIVE = "https://www.googleapis.com/auth/drive"
    const val CONTACTS = "https://www.googleapis.com/auth/contacts"

    fun request(includeDriveAndContacts: Boolean): List<String> = buildList {
        add(OPENID); add(EMAIL); add(PROFILE)
        add(GMAIL_READ); add(GMAIL_SEND); add(CALENDAR)
        if (includeDriveAndContacts) {
            add(DRIVE)
            add(CONTACTS)
        }
    }
}
