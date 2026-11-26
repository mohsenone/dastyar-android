package app.dastyar.agent.connectors

import java.net.URI
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class McpRemoteTool(
    val name: String,
    val description: String,
    val inputSchema: String,
)

/**
 * MCP client for Streamable HTTP (protocol 2025-03-26) with a one-shot SSE fallback
 * for servers that still speak the older HTTP+SSE transport.
 * The SSE GET stays open only until list/call returns.
 */
class McpClient(private val http: OkHttpClient) {
    fun listTools(url: String, transport: String, bearer: String?): List<McpRemoteTool> {
        val session = open(url, transport, bearer)
        return try {
            val body = rpc(session, "tools/list", JSONObject())
            parseTools(body)
        } finally {
            session.close()
        }
    }

    fun callTool(url: String, transport: String, bearer: String?, name: String, arguments: String): String {
        val session = open(url, transport, bearer)
        return try {
            val args = runCatching { JSONObject(if (arguments.isBlank()) "{}" else arguments) }.getOrElse { JSONObject() }
            val body = rpc(session, "tools/call", JSONObject().put("name", name).put("arguments", args))
            renderCall(body)
        } finally {
            session.close()
        }
    }

    private fun open(url: String, transport: String, bearer: String?): Session {
        if (transport == "sse") return openSse(url, bearer)
        return try {
            openHttp(url, bearer)
        } catch (error: McpException) {
            if (transport == "auto" && error.fallback) openSse(url, bearer) else throw error
        }
    }

    private fun openHttp(url: String, bearer: String?): Session {
        val ids = AtomicInteger(1)
        val initId = ids.getAndIncrement()
        val init = post(url, bearer, null, message(initId, "initialize", initParams()), false)
        if (init.code == 404 || init.code == 405 || init.code == 400) {
            throw McpException("HTTP ${init.code}", fallback = true)
        }
        if (init.code !in 200..299) throw McpException("HTTP ${init.code}: ${init.body.take(300)}", fallback = false)
        val payload = jsonObject(init.body) ?: throw McpException("Initialize did not return JSON", fallback = init.body.isBlank())
        if (payload.has("error")) throw McpException(payload.getJSONObject("error").optString("message", "initialize failed"), false)
        val session = HttpSession(url, bearer, init.session, ids)
        post(url, bearer, init.session, JSONObject().put("jsonrpc", "2.0").put("method", "notifications/initialized").toString(), true)
        return session
    }

    private fun openSse(url: String, bearer: String?): Session {
        val queue = LinkedBlockingQueue<String>()
        val request = Request.Builder().url(url).header("Accept", "text/event-stream").header("User-Agent", USER_AGENT)
        if (!bearer.isNullOrBlank()) request.header("Authorization", "Bearer $bearer")
        val call = http.newCall(request.build())
        val thread = Thread {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        queue.offer("ERR HTTP ${response.code}")
                        return@use
                    }
                    val source = response.body?.source() ?: return@use
                    val data = StringBuilder()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isEmpty()) {
                            val text = data.toString().trim()
                            if (text.isNotEmpty()) queue.offer(text)
                            data.clear()
                        } else if (line.startsWith("data:")) {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").trim())
                        }
                    }
                }
            } catch (error: Exception) {
                queue.offer("ERR ${error.message ?: error.javaClass.simpleName}")
            }
        }
        thread.isDaemon = true
        thread.start()
        val first = queue.poll(15, TimeUnit.SECONDS) ?: throw McpException("SSE timeout waiting for the endpoint", false)
        if (first.startsWith("ERR")) throw McpException(first.removePrefix("ERR ").trim(), false)
        val endpoint = runCatching { URI(url).resolve(first).toString() }.getOrElse { throw McpException("Bad SSE endpoint", false) }
        val ids = AtomicInteger(1)
        val session = SseSession(endpoint, bearer, queue, ids, call)
        rpc(session, "initialize", initParams())
        post(endpoint, bearer, null, JSONObject().put("jsonrpc", "2.0").put("method", "notifications/initialized").toString(), true)
        return session
    }

    private fun rpc(session: Session, method: String, params: JSONObject): JSONObject {
        val id = session.nextId()
        val raw = message(id, method, params)
        when (session) {
            is HttpSession -> {
                val response = post(session.url, session.bearer, session.sessionId, raw, false)
                if (response.code !in 200..299) throw McpException("HTTP ${response.code}: ${response.body.take(400)}", false)
                response.session?.let { session.sessionId = it }
                val obj = jsonObject(response.body) ?: throw McpException("Empty MCP response", false)
                if (obj.has("error")) throw McpException(obj.getJSONObject("error").optString("message", "MCP error"), false)
                return obj.optJSONObject("result") ?: obj
            }
            is SseSession -> {
                post(session.endpoint, session.bearer, null, raw, false)
                val deadline = System.currentTimeMillis() + 30_000
                while (System.currentTimeMillis() < deadline) {
                    val item = session.queue.poll(2, TimeUnit.SECONDS) ?: continue
                    if (item.startsWith("ERR")) throw McpException(item.removePrefix("ERR ").trim(), false)
                    val obj = runCatching { JSONObject(item) }.getOrNull() ?: continue
                    if (obj.optInt("id", -1) != id && obj.optString("id") != id.toString()) continue
                    if (obj.has("error")) throw McpException(obj.getJSONObject("error").optString("message", "MCP error"), false)
                    return obj.optJSONObject("result") ?: obj
                }
                throw McpException("SSE timeout waiting for $method", false)
            }
        }
    }

    private fun post(url: String, bearer: String?, session: String?, json: String, notification: Boolean): HttpResult {
        val builder = Request.Builder().url(url)
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .header("MCP-Protocol-Version", PROTOCOL)
            .header("User-Agent", USER_AGENT)
        if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
        if (!session.isNullOrBlank()) builder.header("Mcp-Session-Id", session)
        builder.post(json.toRequestBody(JSON))
        http.newCall(builder.build()).execute().use { response ->
            val body = if (notification) "" else readBody(response)
            return HttpResult(response.code, body, response.header("Mcp-Session-Id"))
        }
    }

    private fun readBody(response: okhttp3.Response): String {
        val raw = response.body?.string().orEmpty()
        val type = response.header("Content-Type").orEmpty()
        if (!type.contains("text/event-stream") && !raw.trimStart().startsWith("event:") && !raw.trimStart().startsWith("data:")) {
            return raw
        }
        return SsePayload.jsonEvents(raw).lastOrNull { it.trimStart().startsWith("{") } ?: raw
    }

    private fun message(id: Int?, method: String, params: JSONObject): String {
        val obj = JSONObject().put("jsonrpc", "2.0").put("method", method).put("params", params)
        if (id != null) obj.put("id", id)
        return obj.toString()
    }

    private fun initParams(): JSONObject = JSONObject()
        .put("protocolVersion", PROTOCOL)
        .put("capabilities", JSONObject())
        .put("clientInfo", JSONObject().put("name", "Dastyar").put("version", "0.2.0"))

    private fun jsonObject(raw: String): JSONObject? = runCatching { JSONObject(raw) }.getOrNull()

    private fun parseTools(result: JSONObject): List<McpRemoteTool> {
        val tools = result.optJSONArray("tools") ?: JSONArray()
        return buildList {
            for (i in 0 until tools.length()) {
                val tool = tools.optJSONObject(i) ?: continue
                val name = tool.optString("name")
                if (name.isBlank()) continue
                val schema = tool.optJSONObject("inputSchema")?.toString() ?: """{"type":"object"}"""
                add(McpRemoteTool(name, tool.optString("description"), schema))
            }
        }
    }

    private fun renderCall(result: JSONObject): String {
        if (result.has("content")) {
            val content = result.optJSONArray("content") ?: JSONArray()
            val text = buildString {
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i) ?: continue
                    when (part.optString("type")) {
                        "text" -> append(part.optString("text")).append('\n')
                        "image" -> append("[image content omitted; the server returned an image]\n")
                        else -> append(part.toString().take(500)).append('\n')
                    }
                }
            }.trim()
            val error = result.optBoolean("isError")
            return if (error) "MCP tool error\n$text" else text.ifBlank { result.toString().take(4000) }
        }
        return result.toString().take(8000)
    }

    private sealed interface Session {
        fun nextId(): Int
        fun close()
    }

    private class HttpSession(
        val url: String,
        val bearer: String?,
        var sessionId: String?,
        private val ids: AtomicInteger,
    ) : Session {
        override fun nextId(): Int = ids.getAndIncrement()
        override fun close() = Unit
    }

    private class SseSession(
        val endpoint: String,
        val bearer: String?,
        val queue: LinkedBlockingQueue<String>,
        private val ids: AtomicInteger,
        private val call: okhttp3.Call,
    ) : Session {
        override fun nextId(): Int = ids.getAndIncrement()
        override fun close() {
            call.cancel()
        }
    }

    private data class HttpResult(val code: Int, val body: String, val session: String?)

    class McpException(message: String, val fallback: Boolean) : Exception(message)

    private companion object {
        const val PROTOCOL = "2025-03-26"
        const val USER_AGENT = "Dastyar/0.2.0"
        val JSON = "application/json".toMediaType()
    }
}
