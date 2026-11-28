package app.dastyar.agent.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAudioPolicyTest {
    @Test
    fun officialOpenAiIsKnownAndOtherBuiltInsAreNot() {
        assertEquals(AudioHope.KnownYes, ProviderAudioPolicy.route(ProviderKinds.OPENAI, "https://api.openai.com/v1"))
        assertEquals(AudioHope.Probe, ProviderAudioPolicy.route(ProviderKinds.OPENAI, "https://proxy.example/v1"))
        assertEquals(AudioHope.Probe, ProviderAudioPolicy.route(ProviderKinds.CUSTOM, "https://llm.example/v1"))
        listOf(
            ProviderKinds.GROQ to "https://api.groq.com/openai/v1",
            ProviderKinds.OPENROUTER to "https://openrouter.ai/api/v1",
            ProviderKinds.GEMINI to "https://generativelanguage.googleapis.com/v1beta",
            ProviderKinds.ANTHROPIC to "https://api.anthropic.com",
            ProviderKinds.GITHUB_MODELS to "https://models.github.ai/inference",
            ProviderKinds.XAI to "https://api.x.ai/v1",
        ).forEach { (kind, base) ->
            assertEquals(kind, AudioHope.KnownNo, ProviderAudioPolicy.route(kind, base))
        }
    }

    @Test
    fun fallbackRunsOnlyAfterOnDeviceFailureAndAHopefulRoute() {
        val yes = ProviderAudioPolicy.route(ProviderKinds.OPENAI, "https://api.openai.com/v1")
        val groq = ProviderAudioPolicy.route(ProviderKinds.GROQ, "https://api.groq.com/openai/v1")
        val custom = ProviderAudioPolicy.route(ProviderKinds.CUSTOM, "https://llm.example")
        assertTrue(ProviderAudioPolicy.shouldTry(true, yes, false))
        assertTrue(ProviderAudioPolicy.shouldTry(true, yes, true))
        assertFalse(ProviderAudioPolicy.shouldTry(false, yes, false))
        assertFalse(ProviderAudioPolicy.shouldTry(true, groq, false))
        assertTrue(ProviderAudioPolicy.shouldTry(true, custom, false))
        assertFalse(ProviderAudioPolicy.shouldTry(true, custom, true))
    }

    @Test
    fun groqAndOpenAiCanTranscribeAndOtherHostsCannot() {
        assertEquals(AudioHope.KnownYes, ProviderAudioPolicy.transcriptionRoute(ProviderKinds.GROQ, "https://api.groq.com/openai/v1"))
        assertEquals(AudioHope.KnownYes, ProviderAudioPolicy.transcriptionRoute(ProviderKinds.OPENAI, "https://api.openai.com/v1"))
        assertEquals(AudioHope.Probe, ProviderAudioPolicy.transcriptionRoute(ProviderKinds.CUSTOM, "https://llm.example/v1"))
        assertEquals(AudioHope.Probe, ProviderAudioPolicy.transcriptionRoute(ProviderKinds.OPENAI, "https://proxy.example/v1"))
        assertEquals(AudioHope.KnownNo, ProviderAudioPolicy.transcriptionRoute(ProviderKinds.GEMINI, "https://generativelanguage.googleapis.com/v1beta"))
        assertEquals(AudioHope.KnownNo, ProviderAudioPolicy.transcriptionRoute(ProviderKinds.OPENROUTER, "https://openrouter.ai/api/v1"))
        assertEquals(AudioHope.KnownNo, ProviderAudioPolicy.transcriptionRoute(ProviderKinds.ANTHROPIC, "https://api.anthropic.com"))
        assertEquals(AudioHope.KnownYes, ProviderAudioPolicy.transcriptionRoute(ProviderKinds.CUSTOM, "https://api.groq.com/openai/v1"))
        assertTrue(ProviderAudioPolicy.shouldTranscribe(true, AudioHope.KnownYes, true))
        assertFalse(ProviderAudioPolicy.shouldTranscribe(false, AudioHope.KnownYes, false))
        assertTrue(ProviderAudioPolicy.shouldTranscribe(true, AudioHope.Probe, false))
        assertFalse(ProviderAudioPolicy.shouldTranscribe(true, AudioHope.Probe, true))
        assertFalse(ProviderAudioPolicy.shouldTranscribe(true, AudioHope.KnownNo, false))
        assertEquals("whisper-large-v3-turbo", ProviderAudioPolicy.transcriptionModel(ProviderKinds.GROQ, "https://api.groq.com/openai/v1"))
        assertEquals("whisper-1", ProviderAudioPolicy.transcriptionModel(ProviderKinds.OPENAI, "https://api.openai.com/v1"))
        assertEquals("whisper-1", ProviderAudioPolicy.transcriptionModel(ProviderKinds.CUSTOM, "https://llm.example/v1"))
        assertEquals(AudioHope.KnownNo, ProviderAudioPolicy.route(ProviderKinds.GROQ, "https://api.groq.com/openai/v1"))
    }

    @Test
    fun audioUrlsStayOnTheConfiguredBase() {
        assertEquals(
            "https://api.openai.com/v1/audio/speech",
            ProviderAudioPolicy.speechUrl("https://api.openai.com/v1"),
        )
        assertEquals(
            "https://api.openai.com/v1/audio/transcriptions",
            ProviderAudioPolicy.transcriptionUrl("https://api.openai.com/v1/"),
        )
        assertEquals(
            "https://llm.example/v1/audio/speech",
            ProviderAudioPolicy.speechUrl("https://llm.example"),
        )
        assertEquals(
            "https://llm.example/v1/audio/speech",
            ProviderAudioPolicy.speechUrl("https://llm.example/v1/audio/speech"),
        )
    }
}
