package app.dastyar.agent.connectors

import android.content.Context
import android.util.Base64
import android.util.Xml
import app.dastyar.agent.data.SettingsStore
import app.dastyar.agent.security.SecretVault
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.TimeUnit

@Singleton
class ConnectorRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ConnectorStore,
    private val vault: SecretVault,
    private val settings: SettingsStore,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()
    private val mcp = McpClient(http)

    suspend fun test(id: String): String {
        val snap = store.current()
        val result = runCatching {
            when (id) {
                ConnectorIds.GOOGLE -> {
                    val token = googleBearer() ?: return@runCatching "error: Google is not signed in."
                    val (code, body) = get("https://www.googleapis.com/oauth2/v3/userinfo", bearer(token))
                    if (code in 200..299) "connected" else "error: Google HTTP $code ${clip(body, token)}"
                }
                ConnectorIds.GITHUB -> {
                    val token = vault.get(VaultIds.GITHUB) ?: return@runCatching "error: No GitHub token."
                    val (code, body) = get("https://api.github.com/user", githubHeaders(token))
                    if (code in 200..299) "connected" else "error: GitHub HTTP $code ${clip(body, token)}"
                }
                ConnectorIds.TELEGRAM -> telegramCall("getMe", vault.get(VaultIds.TELEGRAM), null).let { (code, body) ->
                    if (code in 200..299 && body.contains("\"ok\":true")) "connected" else "error: Telegram HTTP $code ${clip(body)}"
                }
                ConnectorIds.NOTION -> {
                    val token = vault.get(VaultIds.NOTION) ?: return@runCatching "error: No Notion token."
                    val (code, body) = get("https://api.notion.com/v1/users/me", notion(token))
                    if (code in 200..299) "connected" else "error: Notion HTTP $code ${clip(body, token)}"
                }
                ConnectorIds.SEARCH -> webSearch("dastyar").let { if (it.startsWith("error:")) it else "connected" }
                ConnectorIds.WEATHER -> weather("Tehran", "", "").let { if (it.startsWith("error:")) it else "connected" }
                ConnectorIds.RSS -> {
                    val url = snap.rss.firstOrNull() ?: return@runCatching "error: Add a feed URL first."
                    rssFetch(url).let { if (it.startsWith("error:")) it else "connected" }
                }
                ConnectorIds.WEBHOOK -> if (snap.webhookUrl.isBlank()) "error: Set a webhook URL." else "connected"
                ConnectorIds.MCP -> {
                    if (snap.mcp.isEmpty()) "error: Add an MCP server."
                    else {
                        refreshMcp()
                        val note = store.current().notes[ConnectorIds.MCP].orEmpty()
                        if (note.startsWith("error")) note else "connected"
                    }
                }
                ConnectorIds.SHIZUKU -> {
                    val status = ShizukuBridge.status()
                    if (status == "ready") "ready" else "error: $status"
                }
                else -> "error: Unknown connector."
            }
        }.getOrElse { "error: ${it.message ?: it.javaClass.simpleName}" }
        store.setNote(id, result)
        return result
    }

    suspend fun gmailSearch(query: String): String = google { token ->
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val (code, body) = get("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=6&q=$q", bearer(token))
        if (code !in 200..299) return@google "error: Gmail HTTP $code ${clip(body, token)}"
        val ids = JSONObject(body).optJSONArray("messages") ?: JSONArray()
        if (ids.length() == 0) return@google "No messages."
        buildString {
            for (i in 0 until ids.length().coerceAtMost(5)) {
                val id = ids.getJSONObject(i).optString("id")
                val (c2, b2) = get("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date", bearer(token))
                if (c2 !in 200..299) continue
                val msg = JSONObject(b2)
                append(id).append(" · ").append(msg.optString("snippet")).append('\n')
            }
        }.ifBlank { "No readable messages." }
    }

    suspend fun gmailSend(to: String, subject: String, body: String): String = google { token ->
        if (!to.contains('@')) return@google "error: A recipient email is required."
        val raw = "To: $to\r\nSubject: $subject\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n$body"
        val encoded = Base64.encodeToString(raw.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        val payload = JSONObject().put("raw", encoded).toString()
        val (code, text) = send("https://gmail.googleapis.com/gmail/v1/users/me/messages/send", "POST", payload, bearer(token))
        if (code in 200..299) "Sent ${JSONObject(text).optString("id")}" else "error: Gmail HTTP $code ${clip(text, token)}"
    }

    suspend fun calendarList(): String = google { token ->
        val min = java.time.Instant.now().toString()
        val (code, body) = get(
            "https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=10&singleEvents=true&orderBy=startTime&timeMin=${java.net.URLEncoder.encode(min, "UTF-8")}",
            bearer(token),
        )
        if (code !in 200..299) return@google "error: Calendar HTTP $code ${clip(body, token)}"
        val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
        if (items.length() == 0) "No upcoming events." else buildString {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val start = item.optJSONObject("start")?.optString("dateTime").orEmpty().ifBlank { item.optJSONObject("start")?.optString("date").orEmpty() }
                append(item.optString("id")).append(" · ").append(start).append(" · ").append(item.optString("summary")).append('\n')
            }
        }
    }

    suspend fun calendarCreate(title: String, start: String, end: String): String = google { token ->
        val payload = JSONObject()
            .put("summary", title)
            .put("start", JSONObject().put("dateTime", start))
            .put("end", JSONObject().put("dateTime", end))
            .toString()
        val (code, body) = send("https://www.googleapis.com/calendar/v3/calendars/primary/events", "POST", payload, bearer(token))
        if (code in 200..299) "Created ${JSONObject(body).optString("id")}" else "error: Calendar HTTP $code ${clip(body, token)}"
    }

    suspend fun calendarUpdate(eventId: String, title: String, start: String, end: String): String = google { token ->
        val payload = JSONObject()
        if (title.isNotBlank()) payload.put("summary", title)
        if (start.isNotBlank()) payload.put("start", JSONObject().put("dateTime", start))
        if (end.isNotBlank()) payload.put("end", JSONObject().put("dateTime", end))
        val (code, body) = send("https://www.googleapis.com/calendar/v3/calendars/primary/events/$eventId", "PATCH", payload.toString(), bearer(token))
        if (code in 200..299) "Updated $eventId" else "error: Calendar HTTP $code ${clip(body, token)}"
    }

    suspend fun calendarDelete(eventId: String): String = google { token ->
        val (code, body) = send("https://www.googleapis.com/calendar/v3/calendars/primary/events/$eventId", "DELETE", null, bearer(token))
        if (code in 200..299 || code == 204) "Deleted $eventId" else "error: Calendar HTTP $code ${clip(body, token)}"
    }

    suspend fun driveList(query: String): String = google { token ->
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val (code, body) = get(
            "https://www.googleapis.com/drive/v3/files?pageSize=15&fields=files(id,name,mimeType,modifiedTime,size)&q=$q",
            bearer(token),
        )
        if (code !in 200..299) return@google "error: Drive HTTP $code ${clip(body, token)}. Drive needs the Drive scope and may require Google verification."
        val files = JSONObject(body).optJSONArray("files") ?: JSONArray()
        if (files.length() == 0) "No files." else buildString {
            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                append(file.optString("id")).append(" · ").append(file.optString("name")).append(" · ").append(file.optString("mimeType")).append('\n')
            }
        }
    }

    suspend fun driveDownload(fileId: String): String = google { token ->
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@google "error: Drive HTTP ${response.code}"
            val dir = File(context.filesDir, "drive").apply { mkdirs() }
            val dest = File(dir, fileId.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            response.body?.byteStream()?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            "Saved ${dest.absolutePath} (${dest.length()} bytes)"
        }
    }

    suspend fun driveUpload(path: String, name: String): String = google { token ->
        val file = File(path)
        val root = context.filesDir.canonicalFile
        if (!file.canonicalFile.path.startsWith(root.path)) return@google "error: Upload only from app storage."
        if (!file.isFile) return@google "error: File not found."
        val meta = JSONObject().put("name", name.ifBlank { file.name }).toString().toRequestBody(JSON)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addPart(okhttp3.Headers.headersOf("Content-Type", "application/json"), meta)
            .addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.isSuccessful) "Uploaded ${JSONObject(text).optString("id")}" else "error: Drive HTTP ${response.code} ${clip(text, token)}"
        }
    }

    suspend fun contactsSearch(query: String): String = google { token ->
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val (code, body) = get(
            "https://people.googleapis.com/v1/people:searchContacts?query=$q&pageSize=8&readMask=names,emailAddresses,phoneNumbers",
            bearer(token),
        )
        if (code !in 200..299) return@google "error: Contacts HTTP $code ${clip(body, token)}. This scope may need Google verification."
        body.take(4000)
    }

    suspend fun contactsCreate(name: String, phone: String, email: String): String = google { token ->
        val person = JSONObject().put("names", JSONArray().put(JSONObject().put("givenName", name)))
        if (phone.isNotBlank()) person.put("phoneNumbers", JSONArray().put(JSONObject().put("value", phone)))
        if (email.isNotBlank()) person.put("emailAddresses", JSONArray().put(JSONObject().put("value", email)))
        val (code, body) = send("https://people.googleapis.com/v1/people:createContact", "POST", person.toString(), bearer(token))
        if (code in 200..299) "Created contact" else "error: Contacts HTTP $code ${clip(body, token)}"
    }

    suspend fun github(path: String): String {
        val token = vault.get(VaultIds.GITHUB) ?: return "error: GitHub is not connected."
        val (code, body) = get("https://api.github.com$path", githubHeaders(token))
        return if (code in 200..299) clip(body, token) else "error: GitHub HTTP $code ${clip(body, token)}"
    }

    suspend fun githubWrite(path: String, payload: String): String {
        val token = vault.get(VaultIds.GITHUB) ?: return "error: GitHub is not connected."
        val (code, body) = send("https://api.github.com$path", "POST", payload, githubHeaders(token))
        return if (code in 200..299) clip(body, token) else "error: GitHub HTTP $code ${clip(body, token)}"
    }

    suspend fun telegramUpdates(): String {
        val token = vault.get(VaultIds.TELEGRAM) ?: return "error: No bot token."
        val (code, body) = telegramCall("getUpdates?limit=15", token, null)
        return if (code in 200..299) clip(body, token) else "error: Telegram HTTP $code"
    }

    suspend fun telegramSend(chatId: String, text: String): String {
        val token = vault.get(VaultIds.TELEGRAM) ?: return "error: No bot token. This is the bot API, not your personal Telegram account."
        val payload = JSONObject().put("chat_id", chatId).put("text", text).toString()
        val (code, body) = telegramCall("sendMessage", token, payload)
        return if (code in 200..299) "Sent" else "error: Telegram HTTP $code ${clip(body, token)}"
    }

    suspend fun telegramSendFile(chatId: String, path: String, caption: String): String {
        val token = vault.get(VaultIds.TELEGRAM) ?: return "error: No bot token. This is the bot API, not your personal Telegram account."
        val file = File(path)
        val root = context.filesDir.canonicalFile
        val cache = context.cacheDir.canonicalFile
        val canonical = runCatching { file.canonicalFile }.getOrNull()
            ?: return "error: The file path is not readable."
        val allowed = canonical.path.startsWith(root.path) || canonical.path.startsWith(cache.path)
        if (!allowed || !canonical.isFile) {
            return "error: The file must already be in app storage. Copy it there first. The bot API does not read the rest of the phone."
        }
        val url = "https://api.telegram.org/bot$token/sendDocument"
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", caption.take(900))
            .addFormDataPart("document", canonical.name, canonical.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        val request = Request.Builder().url(url).post(body).header("User-Agent", "Dastyar/0.2.0").build()
        val response = http.newCall(request).execute()
        val text = response.body?.string().orEmpty()
        return if (response.isSuccessful) "Sent file" else "error: Telegram HTTP ${response.code} ${clip(text, token)}"
    }

    suspend fun notionSearch(query: String): String = notion("POST", "https://api.notion.com/v1/search", JSONObject().put("query", query).toString())

    suspend fun notionRead(blockId: String): String = notion("GET", "https://api.notion.com/v1/blocks/$blockId/children?page_size=20", null)

    suspend fun notionAppend(blockId: String, text: String): String {
        val payload = JSONObject().put(
            "children",
            JSONArray().put(
                JSONObject()
                    .put("object", "block")
                    .put("type", "paragraph")
                    .put("paragraph", JSONObject().put("rich_text", JSONArray().put(JSONObject().put("type", "text").put("text", JSONObject().put("content", text.take(1800)))))),
            ),
        ).toString()
        return notion("PATCH", "https://api.notion.com/v1/blocks/$blockId/children", payload)
    }

    suspend fun notionCreate(parentId: String, title: String): String {
        val payload = JSONObject()
            .put("parent", JSONObject().put("page_id", parentId))
            .put(
                "properties",
                JSONObject().put(
                    "title",
                    JSONObject().put(
                        "title",
                        JSONArray().put(
                            JSONObject().put("type", "text").put("text", JSONObject().put("content", title.take(200))),
                        ),
                    ),
                ),
            )
            .toString()
        return notion("POST", "https://api.notion.com/v1/pages", payload)
    }

    suspend fun webhook(method: String, url: String, headerName: String, body: String): String {
        val snap = store.current()
        val target = url.ifBlank { snap.webhookUrl }
        val allow = settings.current().webAllowlist
        val rejection = WebhookPolicy.rejection(target, allow)
        if (rejection != null) return "error: $rejection"
        val headers = mutableMapOf("User-Agent" to "Dastyar/0.2.0")
        val secret = vault.get(VaultIds.WEBHOOK)
        if (!secret.isNullOrBlank()) headers[headerName.ifBlank { snap.webhookHeader }] = secret
        val (code, text) = send(target, method.ifBlank { "POST" }.uppercase(), body, headers)
        return "HTTP $code\n${clip(text, secret)}"
    }

    suspend fun webSearch(query: String): String {
        val snap = store.current()
        return when (snap.searchKind) {
            "brave" -> {
                val key = vault.get(VaultIds.BRAVE) ?: return "error: No Brave Search API key. Check the current plan on the official site; this app does not state a price."
                val q = java.net.URLEncoder.encode(query, "UTF-8")
                val (code, body) = get("https://api.search.brave.com/res/v1/web/search?q=$q&count=5", mapOf("X-Subscription-Token" to key, "Accept" to "application/json"))
                if (code !in 200..299) "error: Brave HTTP $code" else clip(body, key)
            }
            "searxng" -> {
                val base = snap.searxngBase.trim().trimEnd('/')
                if (RssUrls.normalize(base) == null && !base.startsWith("https://") && !base.startsWith("http://")) {
                    return "error: Set an https SearXNG base URL."
                }
                if (WebhookPolicy.rejection(base, settings.current().webAllowlist) != null && base.startsWith("http://")) {
                    return "error: Cleartext SearXNG is allowed only when its host is on the allowlist."
                }
                val q = java.net.URLEncoder.encode(query, "UTF-8")
                val (code, body) = get("$base/search?q=$q&format=json", mapOf("Accept" to "application/json"))
                if (code !in 200..299) "error: SearXNG HTTP $code" else body.take(4000)
            }
            else -> {
                val key = vault.get(VaultIds.TAVILY) ?: return "error: No Tavily API key. Check Tavily's current plan; this app does not promise a free quota."
                val payload = JSONObject().put("query", query).put("max_results", 5).toString()
                val (code, body) = send("https://api.tavily.com/search", "POST", payload, mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json"))
                if (code !in 200..299) "error: Tavily HTTP $code" else clip(body, key)
            }
        }
    }

    suspend fun rssFetch(url: String): String {
        val target = RssUrls.normalize(url) ?: return "error: Feed URL must be http(s)."
        if (target.startsWith("http://") && WebhookPolicy.rejection(target, settings.current().webAllowlist) != null) {
            return "error: Cleartext feeds are allowed only when the host is on the allowlist."
        }
        val (code, body) = get(target, mapOf("User-Agent" to "Dastyar/0.2.0"))
        if (code !in 200..299) return "error: Feed HTTP $code"
        return parseFeed(body).ifBlank { "No items." }
    }

    suspend fun weather(place: String, lat: String, lon: String): String {
        val snap = store.current()
        if (snap.weatherKind == "openweather") {
            val key = vault.get(VaultIds.OPENWEATHER) ?: return "error: No OpenWeather key. Check their site for the current plan; this app does not state a price."
            val q = place.ifBlank { return "error: OpenWeather here needs a place name." }
            val (code, body) = get(WeatherUrls.openWeather(q, key), emptyMap())
            return if (code in 200..299) clip(body, key) else "error: OpenWeather HTTP $code"
        }
        val coords = if (lat.isNotBlank() && lon.isNotBlank()) {
            lat.toDoubleOrNull() to lon.toDoubleOrNull()
        } else {
            if (place.isBlank()) return "error: Pass a place name or latitude and longitude."
            val (code, body) = get(WeatherUrls.geocode(place), emptyMap())
            if (code !in 200..299) return "error: Geocoding HTTP $code"
            val results = JSONObject(body).optJSONArray("results") ?: return "error: Place not found."
            if (results.length() == 0) return "error: Place not found."
            val row = results.getJSONObject(0)
            row.optDouble("latitude") to row.optDouble("longitude")
        }
        val la = coords.first ?: return "error: Bad coordinates."
        val lo = coords.second ?: return "error: Bad coordinates."
        val (code, body) = get(WeatherUrls.forecast(la, lo), emptyMap())
        return if (code in 200..299) body.take(2500) else "error: Open-Meteo HTTP $code"
    }

    suspend fun refreshMcp(): String {
        val snap = store.current()
        if (ConnectorIds.MCP !in snap.enabled || snap.mcp.isEmpty()) return "No MCP server is enabled."
        val notes = StringBuilder()
        snap.mcp.forEach { server ->
            val token = vault.get(VaultIds.mcp(server.id))
            val result = runCatching { mcp.listTools(server.url, server.transport.ifBlank { "auto" }, token) }
            result.onSuccess { notes.append(server.name).append(": ").append(it.size).append(" tools\n") }
            result.onFailure { notes.append(server.name).append(": error: ").append(it.message).append('\n') }
        }
        val text = notes.toString().trim()
        store.setNote(ConnectorIds.MCP, if (text.contains("error:")) "error: $text" else "connected")
        return text
    }

    suspend fun mcpTools(): List<Pair<McpServer, McpRemoteTool>> {
        val snap = store.current()
        if (ConnectorIds.MCP !in snap.enabled) return emptyList()
        return snap.mcp.flatMap { server ->
            val token = vault.get(VaultIds.mcp(server.id))
            val tools = runCatching { mcp.listTools(server.url, server.transport.ifBlank { "auto" }, token) }.getOrDefault(emptyList())
            tools.map { server to it }
        }
    }

    suspend fun mcpCall(server: McpServer, remoteName: String, arguments: String): String {
        val token = vault.get(VaultIds.mcp(server.id))
        return runCatching { mcp.callTool(server.url, server.transport.ifBlank { "auto" }, token, remoteName, arguments) }
            .getOrElse { "error: ${it.message ?: "MCP call failed"}" }
    }

    private suspend fun google(block: suspend (String) -> String): String {
        val token = googleBearer() ?: return "error: Google needs a signed-in account. Add a client id and use Sign in."
        return runCatching { block(token) }.getOrElse { "error: ${it.message ?: it.javaClass.simpleName}" }
    }

    private suspend fun googleBearer(): String? {
        val raw = vault.get(VaultIds.GOOGLE_TOKENS) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val access = json.optString("access_token")
        val expiry = json.optLong("expires_at")
        if (access.isNotBlank() && expiry > System.currentTimeMillis() + 60_000) return access
        val refresh = json.optString("refresh_token")
        if (refresh.isBlank()) return access.ifBlank { null }
        val snap = store.current()
        val secret = vault.get(VaultIds.GOOGLE_SECRET).orEmpty()
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", snap.googleClientId)
        if (secret.isNotBlank()) form.add("client_secret", secret)
        val request = Request.Builder().url("https://oauth2.googleapis.com/token").post(form.build()).build()
        val response = http.newCall(request).execute()
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) return null
        val token = JSONObject(text)
        json.put("access_token", token.optString("access_token"))
        if (token.optString("refresh_token").isNotBlank()) json.put("refresh_token", token.optString("refresh_token"))
        json.put("expires_at", System.currentTimeMillis() + token.optLong("expires_in", 3600) * 1000)
        vault.put(VaultIds.GOOGLE_TOKENS, json.toString())
        return json.optString("access_token").ifBlank { null }
    }

    private suspend fun notion(method: String, url: String, body: String?): String {
        val token = vault.get(VaultIds.NOTION) ?: return "error: No Notion token."
        val (code, text) = if (method == "GET") get(url, notion(token)) else send(url, method, body, notion(token))
        return if (code in 200..299) clip(text, token) else "error: Notion HTTP $code ${clip(text, token)}"
    }

    private fun telegramCall(method: String, token: String?, body: String?): Pair<Int, String> {
        if (token.isNullOrBlank()) return 0 to ""
        val url = "https://api.telegram.org/bot$token/$method"
        return if (body == null) get(url, mapOf("User-Agent" to "Dastyar/0.2.0")) else send(url, "POST", body, mapOf("Content-Type" to "application/json"))
    }

    private fun get(url: String, headers: Map<String, String>): Pair<Int, String> = send(url, "GET", null, headers)

    private fun send(url: String, method: String, body: String?, headers: Map<String, String>): Pair<Int, String> {
        val builder = Request.Builder().url(url).header("User-Agent", "Dastyar/0.2.0")
        headers.forEach { (key, value) -> builder.header(key, value) }
        if (method == "GET") builder.get() else builder.method(method, (body ?: "").toRequestBody(JSON))
        http.newCall(builder.build()).execute().use { response ->
            return response.code to response.body?.string().orEmpty().take(100_000)
        }
    }

    private fun bearer(token: String) = mapOf("Authorization" to "Bearer $token")

    private fun githubHeaders(token: String) = mapOf(
        "Authorization" to "Bearer $token",
        "Accept" to "application/vnd.github+json",
        "X-GitHub-Api-Version" to "2022-11-28",
    )

    private fun notion(token: String) = mapOf(
        "Authorization" to "Bearer $token",
        "Notion-Version" to "2022-06-28",
    )

    private fun parseFeed(xml: String): String {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val out = StringBuilder()
        var title = ""
        var link = ""
        var inItem = false
        var tag = ""
        var count = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT && count < 12) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    if (tag == "item" || tag == "entry") {
                        inItem = true
                        title = ""
                        link = ""
                    }
                    if (inItem && tag == "link") {
                        val href = parser.getAttributeValue(null, "href")
                        if (!href.isNullOrBlank() && link.isBlank()) link = href
                    }
                }
                XmlPullParser.TEXT -> if (inItem && tag == "title") title += parser.text.orEmpty()
                XmlPullParser.END_TAG -> if (parser.name == "item" || parser.name == "entry") {
                    if (title.isNotBlank()) {
                        out.append(title.trim()).append(" · ").append(link.trim()).append('\n')
                        count++
                    }
                    inItem = false
                }
            }
            parser.next()
        }
        return out.toString().trim()
    }

    private fun clip(text: String, vararg secrets: String?): String {
        var out = text
        secrets.forEach { secret ->
            if (!secret.isNullOrBlank() && secret.length > 4) out = out.replace(secret, "••••")
        }
        return out.take(4000)
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
