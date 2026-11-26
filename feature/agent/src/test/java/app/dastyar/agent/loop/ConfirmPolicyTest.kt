package app.dastyar.agent.loop

import app.dastyar.agent.tools.RiskLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmPolicyTest {
    @Test
    fun safeToolsSkipTheSheet() {
        assertFalse(shouldConfirm(RiskLevel.SAFE, routineUnattended = false))
    }

    @Test
    fun dangerousToolsAskUnlessTheRoutineWasExplicitlyUnattended() {
        assertTrue(shouldConfirm(RiskLevel.DANGEROUS, routineUnattended = false))
        assertFalse(shouldConfirm(RiskLevel.DANGEROUS, routineUnattended = true))
    }

    @Test
    fun confirmationStillRequiredByDefault() {
        assertTrue(shouldConfirm(RiskLevel.CONFIRM, routineUnattended = false))
    }

    @Test
    fun autoStillAsksForTheSafetyFloor() {
        assertTrue(
            ConfirmPolicy.shouldConfirm(
                RiskLevel.SAFE,
                ConfirmPolicy.Mode.AUTO,
                safetyFloor = true,
                routineUnattended = false,
            ),
        )
        assertFalse(
            ConfirmPolicy.shouldConfirm(
                RiskLevel.DANGEROUS,
                ConfirmPolicy.Mode.AUTO,
                safetyFloor = true,
                routineUnattended = true,
            ),
        )
        assertTrue(
            ConfirmPolicy.shouldConfirm(
                RiskLevel.SAFE,
                ConfirmPolicy.Mode.ALWAYS,
                safetyFloor = false,
                routineUnattended = true,
            ),
        )
    }

    @Test
    fun sendsDeletesAndCallsAreOnTheFloor() {
        assertTrue(ConfirmPolicy.isSafetyFloor("send_sms"))
        assertTrue(ConfirmPolicy.isSafetyFloor("place_call"))
        assertTrue(ConfirmPolicy.isSafetyFloor("google_gmail_send"))
        assertTrue(ConfirmPolicy.isSafetyFloor("telegram_bot_send_file"))
        assertTrue(ConfirmPolicy.isSafetyFloor("webhook_request"))
        assertTrue(ConfirmPolicy.isSafetyFloor("delete_file"))
        assertFalse(ConfirmPolicy.isSafetyFloor("web_fetch", """{"method":"GET"}"""))
        assertTrue(ConfirmPolicy.isSafetyFloor("web_fetch", """{"method":"POST"}"""))
        assertTrue(ConfirmPolicy.isSafetyFloor("app_gmail", """{"tap_send":true}"""))
        assertTrue(ConfirmPolicy.isSafetyFloor("mcp_home_send_mail"))
        assertFalse(ConfirmPolicy.isSafetyFloor("mcp_home_list"))
        assertFalse(ConfirmPolicy.isSafetyFloor("read_sms"))
    }
}
