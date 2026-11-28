package app.dastyar.agent.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePipelineTest {
    @Test
    fun everyRecognizerCodeMapsToABilingualMessage() {
        val codes = listOf(
            SpeechErrors.NETWORK_TIMEOUT to ListenError.Network,
            SpeechErrors.NETWORK to ListenError.Network,
            SpeechErrors.AUDIO to ListenError.Audio,
            SpeechErrors.SERVER to ListenError.Server,
            SpeechErrors.CLIENT to ListenError.Client,
            SpeechErrors.SPEECH_TIMEOUT to ListenError.NoMatch,
            SpeechErrors.NO_MATCH to ListenError.NoMatch,
            SpeechErrors.RECOGNIZER_BUSY to ListenError.Busy,
            SpeechErrors.INSUFFICIENT_PERMISSIONS to ListenError.Permission,
            SpeechErrors.TOO_MANY_REQUESTS to ListenError.Busy,
            SpeechErrors.SERVER_DISCONNECTED to ListenError.Server,
            SpeechErrors.LANGUAGE_NOT_SUPPORTED to ListenError.Language,
            SpeechErrors.LANGUAGE_UNAVAILABLE to ListenError.Language,
            SpeechErrors.CANNOT_CHECK_SUPPORT to ListenError.Unavailable,
            99 to ListenError.Unknown,
        )
        codes.forEach { (code, error) ->
            assertEquals(error, SpeechErrors.listenError(code))
            val en = SpeechErrors.message(error, persian = false)
            val fa = SpeechErrors.message(error, persian = true)
            assertTrue(en.isNotBlank())
            assertTrue(fa.isNotBlank())
            assertFalse(en == fa)
        }
        assertTrue(SpeechErrors.engineUnusable(SpeechErrors.CLIENT))
        assertTrue(SpeechErrors.engineUnusable(SpeechErrors.LANGUAGE_NOT_SUPPORTED))
        assertFalse(SpeechErrors.engineUnusable(SpeechErrors.NO_MATCH))
        assertFalse(SpeechErrors.engineUnusable(SpeechErrors.NETWORK))
        assertTrue(SpeechErrors.message(ListenError.Client, false).contains("rejected"))
        assertFalse(SpeechErrors.message(ListenError.MissingKey, false).contains("free forever"))
    }

    @Test
    fun ownStubIsNotAnEngineAndGoogleBeatsTheOemList() {
        val own = "app.dastyar.agent/app.dastyar.agent.DastyarRecognitionService"
        assertNull(SpeechEnginePicker.bestOnline(listOf(own), "app.dastyar.agent"))
        assertEquals(SpeechEnginePicker.Kind.None, SpeechEnginePicker.choose(listOf(own), "app.dastyar.agent", false))
        assertEquals(SpeechEnginePicker.Kind.OnDevice, SpeechEnginePicker.choose(listOf(own), "app.dastyar.agent", true))
        val google = "com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
        val samsung = "com.samsung.android.bixby.agent/com.samsung.android.voice.Recognizer"
        assertEquals(google, SpeechEnginePicker.bestOnline(listOf(samsung, own, google), "app.dastyar.agent"))
        assertEquals(SpeechEnginePicker.Kind.Online, SpeechEnginePicker.choose(listOf(google), "app.dastyar.agent", true))
        assertTrue(SpeechVisibility.packages.contains("com.google.android.tts"))
        assertTrue(SpeechVisibility.packages.contains("com.xiaomi.mibrain.speech"))
        assertTrue(SpeechVisibility.actions.contains("android.speech.RecognitionService"))
    }

    @Test
    fun finalTextSurvivesAnEmptyResultWhenAPartialWasHeard() {
        assertEquals("سلام", RecognitionText.first(listOf("سلام", "salaam"), null))
        assertEquals("hello", RecognitionText.first(emptyList(), "hello", "older"))
        assertEquals("older", RecognitionText.first(listOf("  "), "  ", "older"))
        assertEquals("", RecognitionText.first(null, null))
    }

    @Test
    fun errorsLeaveTheStageAndClearOnTheNextTurn() {
        val listening = VoiceSnap(phase = VoicePhase.Listening, handsFree = true, listening = true)
        val missed = VoiceSnapRules.showError(listening, ListenError.NoMatch, persian = false)
        assertEquals(VoicePhase.Error, missed.phase)
        assertTrue(missed.handsFree)
        assertTrue(missed.banner!!.contains("try again"))
        val dismissed = VoiceSnapRules.dismiss(missed)
        assertEquals(VoicePhase.Idle, dismissed.phase)
        assertNull(dismissed.error)
        assertNull(dismissed.banner)
        assertTrue(dismissed.handsFree)
        assertTrue(VoiceSnapRules.shouldRestart(dismissed, 0))
        assertFalse(VoiceSnapRules.shouldRestart(dismissed, SpeechErrors.MAX_AUTO_RESTARTS))

        val denied = VoiceSnapRules.showError(listening, ListenError.Permission, persian = true)
        assertFalse(denied.handsFree)
        assertEquals(VoiceHint.MicSettings, denied.hint)
        val deniedIdle = VoiceSnapRules.dismiss(denied)
        assertEquals(VoicePhase.Idle, deniedIdle.phase)
        assertFalse(deniedIdle.handsFree)
        assertEquals(denied.banner, deniedIdle.banner)
        assertFalse(VoiceSnapRules.shouldRestart(deniedIdle, 0))

        val sent = VoiceSnapRules.onSend(deniedIdle.copy(handsFree = true, banner = "still here", error = ListenError.Network))
        assertNull(sent.banner)
        assertNull(sent.error)
        assertEquals(VoiceHint.None, sent.hint)
        assertEquals(VoicePhase.Processing, sent.phase)

        val heard = VoiceSnapRules.onRecognized(missed.copy(live = "open the chat"))
        assertNull(heard.error)
        assertNull(heard.banner)
        assertEquals("open the chat", heard.live)

        val spoken = VoiceSnapRules.onSpoken(missed)
        assertNull(spoken.banner)
        assertEquals(VoiceHint.None, spoken.hint)

        assertEquals(VoiceSnap(), VoiceSnapRules.onLeave(missed))
        assertNull(VoiceSnapRules.onRetry(denied).banner)
        assertEquals(VoiceHint.None, VoiceSnapRules.clearBanner(deniedIdle).hint)

        val missing = VoiceSnapRules.showMissingVoice(listening, persian = true)
        assertEquals(VoiceHint.InstallTts, missing.hint)
        assertTrue(missing.banner!!.contains("فارسی"))
        assertFalse(VoiceSnapRules.dismiss(missing).handsFree)
    }

    @Test
    fun pathLineNamesThePhoneOrTheKeyOwner() {
        assertEquals("Listening with this phone", ListenPathCopy.line(ListenPath.Phone, "", false))
        assertEquals("شنیدن با همین گوشی", ListenPathCopy.line(ListenPath.OnDevice, "", true))
        assertEquals("Transcribing with Groq (your key)", ListenPathCopy.line(ListenPath.Provider, "Groq", false))
        assertEquals("رونویسی با گروک (کلید شما)", ListenPathCopy.line(ListenPath.Provider, "گروک", true))
        assertTrue(ListenPathCopy.line(ListenPath.Provider, "  ", false).contains("your provider"))
    }

    @Test
    fun diagnosticsStayPlainAndMicRulesMatchTheDialog() {
        assertEquals(MicAccess.Granted, MicAccessRules.classify(true, false, false))
        assertEquals(MicAccess.Denied, MicAccessRules.classify(false, true, true))
        assertEquals(MicAccess.Denied, MicAccessRules.classify(false, false, false))
        assertEquals(MicAccess.NeedsSettings, MicAccessRules.classify(false, false, true))
        assertEquals(OnDeviceStatus.NotThisAndroid, OnDeviceRules.status(26, true))
        assertEquals(OnDeviceStatus.Available, OnDeviceRules.status(31, true))
        assertEquals(OnDeviceStatus.Unavailable, OnDeviceRules.status(34, false))

        val snap = VoiceDiagnostics(
            systemRecognizer = false,
            onDevice = OnDeviceStatus.NotThisAndroid,
            ttsEngine = null,
            faVoice = false,
            enVoice = true,
            mic = MicAccess.NeedsSettings,
        )
        val en = VoiceDiagnosticsCopy.lines(snap, false)
        assertTrue(en.any { it == "System recognizer: No" })
        assertTrue(en.any { it == "On-device recognizer: Not on this Android version" })
        assertTrue(en.any { it == "Speech engine: None found" })
        assertTrue(en.any { it == "Persian voice: Not installed" })
        assertTrue(en.any { it == "English voice: Installed" })
        assertTrue(en.any { it == "Microphone: Needs settings" })
        val fa = VoiceDiagnosticsCopy.lines(snap.copy(faVoice = null, systemRecognizer = true), true)
        assertEquals("عیب‌یابی صدا", VoiceDiagnosticsCopy.title(true))
        assertTrue(fa.any { it.contains("بله") })
        assertTrue(fa.any { it.contains("گزارش نشد") })
        assertTrue(VoiceDiagnosticsCopy.checking(false).contains("Checking"))
    }

    @Test
    fun transcriptionOrderPrefersTheActiveKeyThenGroq() {
        val gemini = TranscriptionCandidate("g", "GEMINI", "https://generativelanguage.googleapis.com", true, "Gemini")
        val groq = TranscriptionCandidate("q", "GROQ", "https://api.groq.com/openai/v1", true, "Groq")
        val openai = TranscriptionCandidate("o", "OPENAI", "https://api.openai.com/v1", true, "OpenAI")
        val custom = TranscriptionCandidate("c", "CUSTOM", "https://llm.example/v1", true, "Local")
        val noKey = groq.copy(id = "empty", hasKey = false)
        val usable = { row: TranscriptionCandidate -> row.kind != "GEMINI" }
        val ordered = TranscriptionChoice.ordered("o", listOf(gemini, noKey, custom, groq, openai), usable)
        assertEquals(listOf("o", "q", "c"), ordered.map { it.id })
        assertEquals(listOf("q"), TranscriptionChoice.ordered(null, listOf(groq, noKey), usable).map { it.id })
    }
}
