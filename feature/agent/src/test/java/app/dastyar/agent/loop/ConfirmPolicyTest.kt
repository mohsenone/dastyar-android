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
}
