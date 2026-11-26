package app.dastyar.agent.loop

import app.dastyar.agent.network.LlmErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransientRetryTest {
    @Test
    fun retriesNetworkAndServerErrorsUntilSomethingWasStreamed() {
        assertTrue(TransientRetry.shouldRetry(LlmErrorCode.NETWORK, serverError = true, emitted = false, attempt = 0))
        assertTrue(TransientRetry.shouldRetry(LlmErrorCode.RATE_LIMIT, serverError = false, emitted = false, attempt = 1))
        assertTrue(TransientRetry.shouldRetry(LlmErrorCode.HTTP, serverError = true, emitted = false, attempt = 0))
        assertFalse(TransientRetry.shouldRetry(LlmErrorCode.HTTP, serverError = false, emitted = false, attempt = 0))
        assertFalse(TransientRetry.shouldRetry(LlmErrorCode.UNAUTHORIZED, serverError = false, emitted = false, attempt = 0))
        assertFalse(TransientRetry.shouldRetry(LlmErrorCode.NETWORK, serverError = true, emitted = true, attempt = 0))
        assertFalse(TransientRetry.shouldRetry(LlmErrorCode.NETWORK, serverError = true, emitted = false, attempt = 2))
    }

    @Test
    fun backoffGrowsAndStackTracesStayHidden() {
        assertEquals(400L, TransientRetry.delayMs(0))
        assertEquals(800L, TransientRetry.delayMs(1))
        assertEquals(
            "boom",
            TransientRetry.publicError("java.lang.IllegalStateException: boom\n\tat app.Example.run(Example.kt:1)", "The request failed."),
        )
        assertEquals(
            "The request failed.",
            TransientRetry.publicError("\tat app.Example.run(Example.kt:1)", "The request failed."),
        )
        assertEquals("Busy", TransientRetry.publicError("Busy", "The request failed."))
    }
}
