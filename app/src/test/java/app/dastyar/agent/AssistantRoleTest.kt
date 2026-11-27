package app.dastyar.agent

import app.dastyar.agent.ui.ChromeMetrics
import app.dastyar.agent.voice.AssistantRole
import app.dastyar.agent.voice.AssistantTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRoleTest {
    @Test
    fun roleRequestOnlyWhenThePlatformIntentResolves() {
        assertEquals(AssistantTarget.RoleRequest, AssistantRole.select(29, true))
        assertEquals(AssistantTarget.RoleRequest, AssistantRole.select(35, true))
        assertEquals(AssistantTarget.VoiceInputSettings, AssistantRole.select(29, false))
        assertEquals(AssistantTarget.VoiceInputSettings, AssistantRole.select(28, true))
        assertEquals(AssistantTarget.VoiceInputSettings, AssistantRole.select(26, false))
    }

    @Test
    fun statusCopyIsBilingualAndHonestWhenUnknown() {
        assertEquals("Dastyar is the default digital assistant.", AssistantRole.status(true, false))
        assertEquals("Not the default digital assistant yet.", AssistantRole.status(false, false))
        assertTrue(AssistantRole.status(null, false).contains("does not report"))
        assertEquals("دستیار پیش‌فرض این گوشی است.", AssistantRole.status(true, true))
        assertEquals("هنوز دستیار پیش‌فرض نیست.", AssistantRole.status(false, true))
        assertTrue(AssistantRole.status(null, true).contains("گزارش نمی‌کند"))
    }
}

class ChromeMetricsTest {
    @Test
    fun topBarStaysCompact() {
        assertTrue(ChromeMetrics.TOP_BAR_DP <= 48)
        assertEquals(48, ChromeMetrics.TOP_BAR_DP)
    }
}
