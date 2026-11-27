package app.dastyar.agent.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePolicyTest {
    @Test
    fun recognizerTagMapsPersianEnglishAndAuto() {
        assertEquals("fa-IR", VoiceLang.recognizerTag(VoiceLang.FA, "en"))
        assertEquals("en-US", VoiceLang.recognizerTag(VoiceLang.EN, "fa"))
        assertEquals("fa-IR", VoiceLang.recognizerTag(VoiceLang.AUTO, "fa"))
        assertEquals("fa-IR", VoiceLang.recognizerTag("auto", "fa-IR"))
        assertEquals("en-US", VoiceLang.recognizerTag(VoiceLang.AUTO, "en"))
        assertEquals("en-US", VoiceLang.recognizerTag("", "de"))
        assertEquals(VoiceLang.AUTO, VoiceLang.normalize(null))
        assertEquals(VoiceLang.FA, VoiceLang.normalize("fa-IR"))
        assertEquals(VoiceLang.EN, VoiceLang.normalize("EN-us"))
        assertEquals(VoiceLang.AUTO, VoiceLang.normalize(" "))
    }

    @Test
    fun autoRequestsDetectionAndExplicitLanguagesDoNot() {
        assertTrue(VoiceLang.requestDetection(VoiceLang.AUTO))
        assertTrue(VoiceLang.requestDetection(""))
        assertFalse(VoiceLang.requestDetection(VoiceLang.FA))
        assertFalse(VoiceLang.requestDetection(VoiceLang.EN))
    }

    @Test
    fun missingVoiceCodesAndPersianVoiceLists() {
        assertTrue(TtsSupport.missing(TtsSupport.MISSING_DATA))
        assertTrue(TtsSupport.missing(TtsSupport.NOT_SUPPORTED))
        assertFalse(TtsSupport.missing(0))
        assertTrue(TtsSupport.persianMissing(TtsSupport.MISSING_DATA, listOf("fa-IR")))
        assertTrue(TtsSupport.persianMissing(TtsSupport.NOT_SUPPORTED, emptyList()))
        assertTrue(TtsSupport.persianMissing(0, listOf("en-US", "en-GB")))
        assertFalse(TtsSupport.persianMissing(0, listOf("en-US", "fa-IR")))
        assertFalse(TtsSupport.persianMissing(0, listOf("fa")))
        assertFalse(TtsSupport.persianMissing(1, emptyList()))
        assertFalse(TtsSupport.persianMissing(0, null))
    }

    @Test
    fun prefersPersianFromLanguageOrReplyText() {
        assertTrue(TtsSupport.prefersPersian("fa", "Hello"))
        assertTrue(TtsSupport.prefersPersian("fa-IR", "Hello"))
        assertTrue(TtsSupport.prefersPersian("en", "سلام، روز بخیر"))
        assertFalse(TtsSupport.prefersPersian("en", "Good morning"))
        assertFalse(TtsSupport.prefersPersian("en-US", ""))
    }

    @Test
    fun microphoneIsNotRequestedUntilTheUserStartsVoice() {
        assertFalse(VoiceGate.shouldRequestOnChatOpen())
        assertEquals(VoiceGateDecision.Idle, VoiceGate.decision(false, false, false))
        assertEquals(VoiceGateDecision.NeedPermission, VoiceGate.decision(true, false, true))
        assertEquals(VoiceGateDecision.NeedPermission, VoiceGate.decision(true, false, false))
        assertEquals(VoiceGateDecision.NoRecognizer, VoiceGate.decision(true, true, false))
        assertEquals(VoiceGateDecision.Listen, VoiceGate.decision(true, true, true))
    }

    @Test
    fun spokenReplyDropsSecretsAndKeepsVisibleText() {
        val spoken = Speakable.reply(
            """
            The weather is clear.
            api_key: sk-secretvalue123
            Authorization: Bearer abc.def
            سلام
            """.trimIndent(),
        )
        assertFalse(spoken.contains("sk-"))
        assertFalse(spoken.contains("Bearer"))
        assertTrue(spoken.contains("The weather is clear."))
        assertTrue(spoken.contains("سلام"))
        assertTrue(Speakable.reply("x".repeat(2000)).length <= 1500)
        assertEquals("", Speakable.reply("   "))
    }
}
