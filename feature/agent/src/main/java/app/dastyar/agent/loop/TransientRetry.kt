package app.dastyar.agent.loop

import app.dastyar.agent.network.LlmErrorCode

/** Transient provider failures. A reply that already streamed is not retried. */
object TransientRetry {
    const val MAX_ATTEMPTS = 3

    fun shouldRetry(code: LlmErrorCode, serverError: Boolean, emitted: Boolean, attempt: Int): Boolean {
        if (emitted || attempt >= MAX_ATTEMPTS - 1) return false
        return code == LlmErrorCode.NETWORK ||
            code == LlmErrorCode.RATE_LIMIT ||
            (code == LlmErrorCode.HTTP && serverError)
    }

    fun delayMs(attempt: Int): Long = 400L shl attempt.coerceIn(0, 3)

    fun publicError(raw: String, fallback: String): String {
        val line = raw.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("at ") }
            .orEmpty()
        if (line.isEmpty()) return fallback
        val cleaned = if (line.contains("Exception")) line.substringAfterLast(": ").trim() else line
        if (cleaned.isEmpty() || cleaned.contains("Exception") || cleaned.startsWith("java.") || cleaned.startsWith("kotlin.")) {
            return fallback
        }
        return cleaned.take(240)
    }
}
