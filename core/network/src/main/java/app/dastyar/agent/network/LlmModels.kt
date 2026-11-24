package app.dastyar.agent.network

object ProviderKinds {
    const val GEMINI = "GEMINI"
    const val OPENROUTER = "OPENROUTER"
    const val GROQ = "GROQ"
    const val CLOUDFLARE = "CLOUDFLARE"
    const val NVIDIA = "NVIDIA"
    const val CEREBRAS = "CEREBRAS"
    const val OPENAI = "OPENAI"
    const val ANTHROPIC = "ANTHROPIC"
    const val XAI = "XAI"
    const val DEEPSEEK = "DEEPSEEK"
    const val MISTRAL = "MISTRAL"
    const val CUSTOM = "CUSTOM"

    fun supportsVision(kind: String): Boolean = kind in setOf(
        GEMINI, OPENAI, ANTHROPIC, OPENROUTER, CUSTOM, XAI, NVIDIA,
    )
}

enum class LlmErrorCode {
    UNAUTHORIZED,
    FORBIDDEN,
    RATE_LIMIT,
    NETWORK,
    HTTP,
    CANCELLED,
    UNKNOWN,
}

data class LlmOptions(
    val kind: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val accountId: String? = null,
    val extraHeaders: Map<String, String> = emptyMap(),
)

data class LlmMessage(
    val role: String,
    val text: String,
    val toolCallId: String? = null,
    val name: String? = null,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val imageDataUris: List<String> = emptyList(),
)

data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class LlmToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String,
)

data class LlmRequest(
    val messages: List<LlmMessage>,
    val tools: List<LlmToolSpec>,
    val options: LlmOptions,
    val maxTokens: Int = 2048,
)

sealed class LlmStreamEvent {
    data class Text(val delta: String) : LlmStreamEvent()
    data class ToolCalls(val calls: List<LlmToolCall>) : LlmStreamEvent()
    data object Done : LlmStreamEvent()
    data class Failure(
        val code: LlmErrorCode,
        val message: String,
        val suggestProxy: Boolean,
    ) : LlmStreamEvent()
}

data class ConnectionTest(
    val ok: Boolean,
    val latencyMs: Long,
    val message: String,
    val suggestProxy: Boolean,
)

interface LlmClient {
    fun stream(request: LlmRequest): kotlinx.coroutines.flow.Flow<LlmStreamEvent>
    suspend fun test(options: LlmOptions): ConnectionTest
}

internal fun redactSecrets(raw: String): String {
    var text = raw
    text = Regex("(?i)([?&]key=)[^&\\s]+").replace(text, "$1••••")
    text = Regex("sk-[A-Za-z0-9_\\-]{6,}").replace(text, "••••")
    text = Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]{6,}").replace(text, "Bearer ••••")
    text = Regex("(?i)\"api[_-]?key\"\\s*:\\s*\"[^\"]+\"").replace(text, "\"api_key\":\"••••\"")
    return text.take(500)
}
