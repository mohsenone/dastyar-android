package app.dastyar.agent.network

import org.json.JSONArray
import org.json.JSONObject

internal object Sse {
    fun events(raw: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        raw.split('\n').forEach { original ->
            val line = original.removeSuffix("\r")
            if (line.isEmpty()) {
                if (current.isNotEmpty()) {
                    out += current.toString()
                    current.clear()
                }
            } else if (line.startsWith("data:")) {
                if (current.isNotEmpty()) current.append('\n')
                current.append(line.substring(5).trim())
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }
}

internal class OpenAiAccumulator {
    private val text = StringBuilder()
    private val calls = sortedMapOf<Int, Partial>()

    fun consume(payload: String): List<LlmStreamEvent> {
        if (payload == "[DONE]") return finish()
        val root = try {
            JSONObject(payload)
        } catch (_: Exception) {
            return emptyList()
        }
        if (root.has("error")) {
            val message = root.optJSONObject("error")?.optString("message") ?: root.toString()
            return listOf(LlmStreamEvent.Failure(LlmErrorCode.HTTP, redactSecrets(message), false))
        }
        val choice = root.optJSONArray("choices")?.optJSONObject(0) ?: return emptyList()
        val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message")
        val events = mutableListOf<LlmStreamEvent>()
        val chunk = delta?.optString("content").orEmpty()
        if (chunk.isNotEmpty() && chunk != "null") {
            text.append(chunk)
            events += LlmStreamEvent.Text(chunk)
        }
        val toolDeltas = delta?.optJSONArray("tool_calls")
        if (toolDeltas != null) absorbTools(toolDeltas)
        val finish = choice.optString("finish_reason")
        if (finish == "stop" || finish == "tool_calls") {
            // The final tool list is emitted on [DONE] or finish() from the caller.
        }
        return events
    }

    fun finish(): List<LlmStreamEvent> {
        val events = mutableListOf<LlmStreamEvent>()
        val built = calls.values.mapNotNull { it.build() }
        if (built.isNotEmpty()) events += LlmStreamEvent.ToolCalls(built)
        events += LlmStreamEvent.Done
        calls.clear()
        return events
    }

    private fun absorbTools(array: JSONArray) {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val index = obj.optInt("index", i)
            val partial = calls.getOrPut(index) { Partial() }
            val id = obj.optString("id")
            if (id.isNotBlank()) partial.id = id
            val fn = obj.optJSONObject("function")
            val name = fn?.optString("name").orEmpty()
            if (name.isNotBlank()) partial.name = name
            val args = fn?.optString("arguments").orEmpty()
            if (args.isNotEmpty() && args != "null") partial.args.append(args)
        }
    }

    private class Partial {
        var id: String = ""
        var name: String = ""
        val args = StringBuilder()
        fun build(): LlmToolCall? {
            if (name.isBlank() && id.isBlank()) return null
            val arguments = args.toString().ifBlank { "{}" }
            return LlmToolCall(
                id = id.ifBlank { "call_$name" },
                name = name,
                arguments = arguments,
            )
        }
    }
}

internal fun fallbackToolCalls(text: String): List<LlmToolCall>? {
    val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    if (!trimmed.startsWith("{") || !trimmed.contains("tool_calls")) return null
    return try {
        val root = JSONObject(trimmed)
        val array = root.optJSONArray("tool_calls") ?: return null
        val calls = mutableListOf<LlmToolCall>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name").ifBlank { obj.optJSONObject("function")?.optString("name") }.orEmpty()
            if (name.isBlank()) continue
            val args = when {
                obj.has("arguments") && obj.opt("arguments") is JSONObject -> obj.getJSONObject("arguments").toString()
                obj.has("arguments") -> obj.optString("arguments").ifBlank { "{}" }
                else -> "{}"
            }
            calls += LlmToolCall(id = "cf_$i", name = name, arguments = args)
        }
        calls.ifEmpty { null }
    } catch (_: Exception) {
        null
    }
}
