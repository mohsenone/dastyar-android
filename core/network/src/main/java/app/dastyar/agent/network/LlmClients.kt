package app.dastyar.agent.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class OpenAiCompatibleClient @Inject constructor() : LlmClient {
    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = callbackFlow {
        val call = Net.client.newCall(build(request, stream = true))
        try {
            val response = call.execute()
            val body = response.body
            if (!response.isSuccessful || body == null) {
                trySend(Net.failureFor(response.code, body?.string().orEmpty()))
                close()
                return@callbackFlow
            }
            readOpenAi(body) { trySend(it) }
        } catch (error: Exception) {
            if (!call.isCanceled()) trySend(Net.networkFailure(error))
        }
        close()
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    override suspend fun test(options: LlmOptions): ConnectionTest = testCall(options) { opts ->
        build(
            LlmRequest(listOf(LlmMessage(role = "user", text = "Reply with the word pong.")), emptyList(), opts, 32),
            stream = false,
        )
    }

    private fun build(request: LlmRequest, stream: Boolean) = Net.request(
        url = Net.normalizeBase(request.options.baseUrl) + "/chat/completions",
        body = body(request, stream).toString(),
        headers = authHeaders(request.options),
    )

    private fun body(request: LlmRequest, stream: Boolean): JSONObject {
        val root = JSONObject()
            .put("model", request.options.model)
            .put("messages", openAiMessages(request.messages))
            .put("stream", stream)
            .put("max_tokens", request.maxTokens)
        if (request.tools.isNotEmpty()) {
            val tools = JSONArray()
            request.tools.forEach { tool ->
                tools.put(
                    JSONObject()
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", tool.name)
                                .put("description", tool.description)
                                .put("parameters", JSONObject(tool.parametersJson)),
                        ),
                )
            }
            root.put("tools", tools)
        }
        return root
    }
}

@Singleton
class AnthropicClient @Inject constructor() : LlmClient {
    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = callbackFlow {
        val call = Net.client.newCall(build(request, stream = true))
        try {
            val response = call.execute()
            val body = response.body
            if (!response.isSuccessful || body == null) {
                trySend(Net.failureFor(response.code, body?.string().orEmpty()))
                close()
                return@callbackFlow
            }
            val acc = AnthropicAccumulator()
            var finished = false
            body.source().use { source ->
                val data = StringBuilder()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) {
                        if (data.isNotEmpty()) {
                            val payload = data.toString()
                            data.clear()
                            acc.consume(payload).forEach { event ->
                                if (event is LlmStreamEvent.Done) finished = true
                                trySend(event)
                            }
                        }
                    } else if (line.startsWith("data:")) {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.substring(5).trim())
                    }
                }
            }
            if (!finished) acc.finish().forEach { trySend(it) }
        } catch (error: Exception) {
            if (!call.isCanceled()) trySend(Net.networkFailure(error))
        }
        close()
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    override suspend fun test(options: LlmOptions): ConnectionTest = testCall(options) { opts ->
        build(
            LlmRequest(listOf(LlmMessage("user", "Reply with the word pong.")), emptyList(), opts, 32),
            stream = false,
        )
    }

    private fun build(request: LlmRequest, stream: Boolean): okhttp3.Request {
        val headers = request.options.extraHeaders.toMutableMap()
        headers["x-api-key"] = request.options.apiKey
        headers["anthropic-version"] = "2023-06-01"
        val root = JSONObject()
            .put("model", request.options.model)
            .put("max_tokens", request.maxTokens)
            .put("stream", stream)
        val system = request.messages.filter { it.role == "system" }.joinToString("\n\n") { it.text }
        if (system.isNotBlank()) root.put("system", system)
        root.put("messages", anthropicMessages(request.messages.filter { it.role != "system" }))
        if (request.tools.isNotEmpty()) {
            val tools = JSONArray()
            request.tools.forEach { tool ->
                tools.put(
                    JSONObject()
                        .put("name", tool.name)
                        .put("description", tool.description)
                        .put("input_schema", JSONObject(tool.parametersJson)),
                )
            }
            root.put("tools", tools)
        }
        return Net.request(
            url = Net.normalizeBase(request.options.baseUrl) + "/v1/messages",
            body = root.toString(),
            headers = headers,
        )
    }
}

@Singleton
class GeminiClient @Inject constructor() : LlmClient {
    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = callbackFlow {
        val call = Net.client.newCall(build(request, stream = true))
        try {
            val response = call.execute()
            val body = response.body
            if (!response.isSuccessful || body == null) {
                trySend(Net.failureFor(response.code, body?.string().orEmpty()))
                close()
                return@callbackFlow
            }
            readGemini(body) { trySend(it) }
        } catch (error: Exception) {
            if (!call.isCanceled()) trySend(Net.networkFailure(error))
        }
        close()
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    override suspend fun test(options: LlmOptions): ConnectionTest = testCall(options) { opts ->
        build(LlmRequest(listOf(LlmMessage("user", "Reply with the word pong.")), emptyList(), opts, 32), stream = false)
    }

    private fun build(request: LlmRequest, stream: Boolean): okhttp3.Request {
        val base = Net.normalizeBase(request.options.baseUrl)
        val model = request.options.model
        val method = if (stream) "streamGenerateContent" else "generateContent"
        val url = "$base/models/$model:$method" + if (stream) "?alt=sse" else ""
        val root = JSONObject()
        val system = request.messages.filter { it.role == "system" }.joinToString("\n\n") { it.text }
        if (system.isNotBlank()) {
            root.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        }
        root.put("contents", geminiContents(request.messages.filter { it.role != "system" }))
        if (request.tools.isNotEmpty()) {
            val decls = JSONArray()
            request.tools.forEach { tool ->
                decls.put(
                    JSONObject()
                        .put("name", tool.name)
                        .put("description", tool.description)
                        .put("parameters", JSONObject(tool.parametersJson)),
                )
            }
            root.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", decls)))
        }
        root.put(
            "generationConfig",
            JSONObject().put("maxOutputTokens", request.maxTokens),
        )
        val headers = request.options.extraHeaders.toMutableMap()
        headers["x-goog-api-key"] = request.options.apiKey
        return Net.request(url, root.toString(), headers)
    }
}

@Singleton
class CloudflareClient @Inject constructor() : LlmClient {
    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = callbackFlow {
        val call = Net.client.newCall(build(request))
        try {
            val response = call.execute()
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                trySend(Net.failureFor(response.code, raw))
                close()
                return@callbackFlow
            }
            val text = cloudflareText(raw)
            val tools = fallbackToolCalls(text)
            if (tools != null) {
                trySend(LlmStreamEvent.ToolCalls(tools))
            } else if (text.isNotBlank()) {
                trySend(LlmStreamEvent.Text(text))
            }
            trySend(LlmStreamEvent.Done)
        } catch (error: Exception) {
            if (!call.isCanceled()) trySend(Net.networkFailure(error))
        }
        close()
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    override suspend fun test(options: LlmOptions): ConnectionTest = testCall(options) { build(
        LlmRequest(listOf(LlmMessage("user", "Reply with the word pong.")), emptyList(), it, 32),
    ) }

    private fun build(request: LlmRequest): okhttp3.Request {
        val account = request.options.accountId.orEmpty()
        val model = request.options.model
        val url = "https://api.cloudflare.com/client/v4/accounts/$account/ai/run/$model"
        val messages = JSONArray()
        val toolNote = if (request.tools.isEmpty()) {
            ""
        } else {
            val names = request.tools.joinToString { it.name }
            "\nIf you need a tool, reply with only this JSON and nothing else: {\"tool_calls\":[{\"name\":\"<one of $names>\",\"arguments\":{}}]}"
        }
        request.messages.forEach { msg ->
            val content = if (msg.role == "system") msg.text + toolNote else {
                if (msg.toolCalls.isNotEmpty()) {
                    msg.text + "\n" + JSONObject().put(
                        "tool_calls",
                        JSONArray().apply {
                            msg.toolCalls.forEach { call ->
                                put(JSONObject().put("name", call.name).put("arguments", call.arguments))
                            }
                        },
                    ).toString()
                } else if (msg.role == "tool") {
                    "Tool ${msg.name} result: ${msg.text}"
                } else {
                    msg.text
                }
            }
            val role = when (msg.role) {
                "tool" -> "user"
                else -> msg.role
            }
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        val headers = request.options.extraHeaders.toMutableMap()
        headers["Authorization"] = "Bearer ${request.options.apiKey}"
        return Net.request(url, JSONObject().put("messages", messages).toString(), headers)
    }
}

@Singleton
class LlmClientFactory @Inject constructor(
    private val openAi: OpenAiCompatibleClient,
    private val gemini: GeminiClient,
    private val anthropic: AnthropicClient,
    private val cloudflare: CloudflareClient,
) {
    fun clientFor(kind: String): LlmClient = when (kind) {
        ProviderKinds.GEMINI -> gemini
        ProviderKinds.ANTHROPIC -> anthropic
        ProviderKinds.CLOUDFLARE -> cloudflare
        else -> openAi
    }
}

object ImageEncoder {
    fun dataUri(path: String): String? = Net.jpegDataUri(path)
}

private fun authHeaders(options: LlmOptions): Map<String, String> {
    val headers = linkedMapOf("Authorization" to "Bearer ${options.apiKey}")
    if (options.kind == ProviderKinds.OPENROUTER) {
        headers["HTTP-Referer"] = "https://github.com/mohsenone/dastyar-android"
        headers["X-Title"] = "Dastyar"
    }
    options.extraHeaders.forEach { (key, value) -> headers[key] = value }
    return headers
}

private fun openAiMessages(messages: List<LlmMessage>): JSONArray {
    val array = JSONArray()
    messages.forEach { msg ->
        when (msg.role) {
            "system" -> array.put(JSONObject().put("role", "system").put("content", msg.text))
            "tool" -> array.put(
                JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", msg.toolCallId ?: "")
                    .put("content", msg.text),
            )
            "assistant" -> {
                val obj = JSONObject().put("role", "assistant")
                if (msg.text.isNotEmpty()) obj.put("content", msg.text) else obj.put("content", JSONObject.NULL)
                if (msg.toolCalls.isNotEmpty()) {
                    val calls = JSONArray()
                    msg.toolCalls.forEach { call ->
                        calls.put(
                            JSONObject()
                                .put("id", call.id)
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject().put("name", call.name).put("arguments", call.arguments.ifBlank { "{}" }),
                                ),
                        )
                    }
                    obj.put("tool_calls", calls)
                }
                array.put(obj)
            }
            else -> {
                if (msg.imageDataUris.isEmpty()) {
                    array.put(JSONObject().put("role", "user").put("content", msg.text))
                } else {
                    val content = JSONArray().put(JSONObject().put("type", "text").put("text", msg.text.ifBlank { " " }))
                    msg.imageDataUris.forEach { uri ->
                        content.put(
                            JSONObject()
                                .put("type", "image_url")
                                .put("image_url", JSONObject().put("url", uri)),
                        )
                    }
                    array.put(JSONObject().put("role", "user").put("content", content))
                }
            }
        }
    }
    return array
}

private fun anthropicMessages(messages: List<LlmMessage>): JSONArray {
    val array = JSONArray()
    messages.forEach { msg ->
        when (msg.role) {
            "assistant" -> {
                val content = JSONArray()
                if (msg.text.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", msg.text))
                msg.toolCalls.forEach { call ->
                    val input = try {
                        JSONObject(call.arguments.ifBlank { "{}" })
                    } catch (_: Exception) {
                        JSONObject()
                    }
                    content.put(
                        JSONObject()
                            .put("type", "tool_use")
                            .put("id", call.id)
                            .put("name", call.name)
                            .put("input", input),
                    )
                }
                array.put(JSONObject().put("role", "assistant").put("content", content))
            }
            "tool" -> array.put(
                JSONObject().put("role", "user").put(
                    "content",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "tool_result")
                            .put("tool_use_id", msg.toolCallId ?: "")
                            .put("content", msg.text),
                    ),
                ),
            )
            else -> {
                if (msg.imageDataUris.isEmpty()) {
                    array.put(JSONObject().put("role", "user").put("content", msg.text))
                } else {
                    val content = JSONArray()
                    msg.imageDataUris.forEach { uri ->
                        content.put(
                            JSONObject()
                                .put("type", "image")
                                .put(
                                    "source",
                                    JSONObject()
                                        .put("type", "base64")
                                        .put("media_type", "image/jpeg")
                                        .put("data", Net.rawBase64(uri)),
                                ),
                        )
                    }
                    content.put(JSONObject().put("type", "text").put("text", msg.text.ifBlank { " " }))
                    array.put(JSONObject().put("role", "user").put("content", content))
                }
            }
        }
    }
    return array
}

private fun geminiContents(messages: List<LlmMessage>): JSONArray {
    val array = JSONArray()
    messages.forEach { msg ->
        val role = if (msg.role == "assistant") "model" else "user"
        val parts = JSONArray()
        if (msg.role == "tool") {
            parts.put(
                JSONObject().put(
                    "functionResponse",
                    JSONObject()
                        .put("name", msg.name ?: "tool")
                        .put("response", JSONObject().put("result", msg.text)),
                ),
            )
        } else {
            if (msg.text.isNotBlank()) parts.put(JSONObject().put("text", msg.text))
            msg.imageDataUris.forEach { uri ->
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject().put("mime_type", "image/jpeg").put("data", Net.rawBase64(uri)),
                    ),
                )
            }
            msg.toolCalls.forEach { call ->
                val args = try {
                    JSONObject(call.arguments.ifBlank { "{}" })
                } catch (_: Exception) {
                    JSONObject()
                }
                parts.put(JSONObject().put("functionCall", JSONObject().put("name", call.name).put("args", args)))
            }
        }
        if (parts.length() > 0) array.put(JSONObject().put("role", role).put("parts", parts))
    }
    return array
}

private fun readOpenAi(body: ResponseBody, emit: (LlmStreamEvent) -> Unit) {
    val acc = OpenAiAccumulator()
    var sawData = false
    var finished = false
    val raw = StringBuilder()
    body.source().use { source ->
        val data = StringBuilder()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            raw.append(line).append('\n')
            if (line.isEmpty()) {
                if (data.isNotEmpty()) {
                    sawData = true
                    val payload = data.toString()
                    data.clear()
                    if (payload == "[DONE]") {
                        if (!finished) {
                            finished = true
                            acc.finish().forEach(emit)
                        }
                    } else {
                        acc.consume(payload).forEach { event ->
                            if (event is LlmStreamEvent.Failure) finished = true
                            emit(event)
                        }
                    }
                }
            } else if (line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.substring(5).trim())
            }
        }
        if (data.isNotEmpty()) {
            sawData = true
            val payload = data.toString()
            if (payload != "[DONE]") acc.consume(payload).forEach(emit)
        }
    }
    if (!sawData) {
        val text = raw.toString().trim()
        if (text.startsWith("{")) acc.consume(text).forEach(emit)
    }
    if (!finished) acc.finish().forEach(emit)
}

private fun readGemini(body: ResponseBody, emit: (LlmStreamEvent) -> Unit) {
    val calls = mutableListOf<LlmToolCall>()
    var index = 0
    fun absorb(payload: String) {
        val root = try {
            JSONObject(payload)
        } catch (_: Exception) {
            return
        }
        if (root.has("error")) {
            emit(Net.failureFor(400, payload))
            return
        }
        val parts = root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val text = part.optString("text")
            if (text.isNotBlank() && text != "null") emit(LlmStreamEvent.Text(text))
            val call = part.optJSONObject("functionCall")
            if (call != null) {
                val name = call.optString("name")
                val args = call.optJSONObject("args")?.toString() ?: "{}"
                calls += LlmToolCall(id = "gem_${index++}", name = name, arguments = args)
            }
        }
    }
    val raw = StringBuilder()
    var sawData = false
    body.source().use { source ->
        val data = StringBuilder()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            raw.append(line).append('\n')
            if (line.isEmpty()) {
                if (data.isNotEmpty()) {
                    sawData = true
                    absorb(data.toString())
                    data.clear()
                }
            } else if (line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.substring(5).trim())
            }
        }
        if (data.isNotEmpty()) {
            sawData = true
            absorb(data.toString())
        }
    }
    if (!sawData) {
        val text = raw.toString().trim()
        if (text.startsWith("[")) {
            val array = JSONArray(text)
            for (i in 0 until array.length()) absorb(array.getJSONObject(i).toString())
        } else if (text.startsWith("{")) {
            absorb(text)
        }
    }
    if (calls.isNotEmpty()) emit(LlmStreamEvent.ToolCalls(calls))
    emit(LlmStreamEvent.Done)
}

private class AnthropicAccumulator {
    private val tools = linkedMapOf<Int, PartialTool>()

    fun consume(payload: String): List<LlmStreamEvent> {
        val root = try {
            JSONObject(payload)
        } catch (_: Exception) {
            return emptyList()
        }
        if (root.optString("type") == "error" || root.has("error")) {
            return listOf(Net.failureFor(400, payload))
        }
        val events = mutableListOf<LlmStreamEvent>()
        when (root.optString("type")) {
            "content_block_start" -> {
                val block = root.optJSONObject("content_block")
                if (block?.optString("type") == "tool_use") {
                    val index = root.optInt("index", tools.size)
                    tools[index] = PartialTool(
                        id = block.optString("id").ifBlank { "tool_$index" },
                        name = block.optString("name"),
                    )
                }
            }
            "content_block_delta" -> {
                val delta = root.optJSONObject("delta")
                when (delta?.optString("type")) {
                    "text_delta" -> {
                        val text = delta.optString("text")
                        if (text.isNotEmpty()) events += LlmStreamEvent.Text(text)
                    }
                    "input_json_delta" -> {
                        val index = root.optInt("index", 0)
                        tools.getOrPut(index) { PartialTool("tool_$index", "") }
                            .args.append(delta.optString("partial_json"))
                    }
                }
            }
            "message_stop" -> events += finish()
        }
        return events
    }

    fun finish(): List<LlmStreamEvent> {
        val calls = tools.values.filter { it.name.isNotBlank() }.map {
            LlmToolCall(it.id, it.name, it.args.toString().ifBlank { "{}" })
        }
        val events = mutableListOf<LlmStreamEvent>()
        if (calls.isNotEmpty()) events += LlmStreamEvent.ToolCalls(calls)
        events += LlmStreamEvent.Done
        tools.clear()
        return events
    }

    private class PartialTool(val id: String, var name: String) {
        val args = StringBuilder()
    }
}

private fun cloudflareText(raw: String): String {
    val root = try {
        JSONObject(raw)
    } catch (_: Exception) {
        return raw
    }
    if (root.optBoolean("success", true).not()) {
        return Net.extractMessage(raw)
    }
    val result = root.optJSONObject("result") ?: return root.optString("response")
    result.optString("response").takeIf { it.isNotBlank() }?.let { return it }
    val choice = result.optJSONArray("choices")?.optJSONObject(0)
    val message = choice?.optJSONObject("message")?.optString("content")
    if (!message.isNullOrBlank()) return message
    return result.optString("text")
}

private suspend fun testCall(
    options: LlmOptions,
    request: (LlmOptions) -> okhttp3.Request,
): ConnectionTest = withContext(Dispatchers.IO) {
    val started = System.currentTimeMillis()
    try {
        val response = Net.client.newCall(request(options)).execute()
        val body = response.body?.string().orEmpty()
        val latency = System.currentTimeMillis() - started
        if (!response.isSuccessful) {
            val failure = Net.failureFor(response.code, body)
            ConnectionTest(false, latency, failure.message, failure.suggestProxy)
        } else if (JSONObjectOrNull(body)?.has("error") == true && JSONObjectOrNull(body)?.optJSONObject("error") != null) {
            val failure = Net.failureFor(response.code, body)
            ConnectionTest(false, latency, failure.message, failure.suggestProxy)
        } else {
            ConnectionTest(true, latency, "OK", false)
        }
    } catch (error: Exception) {
        val failure = Net.networkFailure(error)
        ConnectionTest(false, System.currentTimeMillis() - started, failure.message, true)
    }
}

private fun JSONObjectOrNull(text: String): JSONObject? = try {
    JSONObject(text)
} catch (_: Exception) {
    null
}
