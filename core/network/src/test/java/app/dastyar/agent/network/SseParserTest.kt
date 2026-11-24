package app.dastyar.agent.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {
    @Test
    fun splitsSseFrames() {
        val raw = "data: {\"a\":1}\n\ndata: [DONE]\n\n"
        assertEquals(listOf("{\"a\":1}", "[DONE]"), Sse.events(raw))
    }

    @Test
    fun assemblesOpenAiToolCall() {
        val acc = OpenAiAccumulator()
        acc.consume("""{"choices":[{"delta":{"content":"Hi"}}]}""")
        acc.consume(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"list_events","arguments":""}}]}}]}""",
        )
        acc.consume(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]}}]}""",
        )
        val done = acc.finish()
        val calls = done.filterIsInstance<LlmStreamEvent.ToolCalls>().single().calls
        assertEquals("list_events", calls.single().name)
        assertEquals("{}", calls.single().arguments)
        assertTrue(done.last() is LlmStreamEvent.Done)
    }

    @Test
    fun parsesCloudflareFallback() {
        val calls = fallbackToolCalls("""{"tool_calls":[{"name":"open_url","arguments":{"url":"https://example.com"}}]}""")
        assertEquals("open_url", calls?.single()?.name)
    }

    @Test
    fun redactsKeys() {
        val text = redactSecrets("https://example.test/v1?key=supersecretvalue sk-abcDEFghij and bearer abcdefghijklmnop")
        assertTrue(!text.contains("supersecretvalue"))
        assertTrue(!text.contains("sk-abc"))
    }
}
