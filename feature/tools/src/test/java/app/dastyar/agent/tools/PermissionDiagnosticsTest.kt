package app.dastyar.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionDiagnosticsTest {
    @Test
    fun android13MissingSmsIsRestrictedAndNamesTheNextStep() {
        val report = PermissionDiagnostics.aggregate(FakeProbe(sdkInt = 34))
        assertTrue(report.restrictedLikely)
        assertEquals(AccessState.MAYBE_RESTRICTED, report.items.first { it.id == "sms" }.state)
        assertEquals(AccessState.MAYBE_RESTRICTED, report.items.first { it.id == "call_log" }.state)
        assertEquals(AccessState.MAYBE_RESTRICTED, report.items.first { it.id == "accessibility" }.state)
        assertEquals(AccessState.MAYBE_RESTRICTED, report.items.first { it.id == "notification_listener" }.state)
        assertEquals(AccessState.MISSING, report.items.first { it.id == "camera" }.state)
        assertEquals(AccessState.NEEDS_SETTINGS, report.items.first { it.id == "overlay" }.state)
        val text = report.render("fa")
        assertTrue(text.contains("sms: MAYBE_RESTRICTED"))
        assertTrue(text.contains("اجازه به تنظیمات محدود"))
        assertTrue(text.contains("⋮"))
    }

    @Test
    fun olderAndroidDoesNotBlameRestrictedSettings() {
        val report = PermissionDiagnostics.aggregate(FakeProbe(sdkInt = 28))
        assertFalse(report.restrictedLikely)
        assertEquals(AccessState.MISSING, report.items.first { it.id == "sms" }.state)
        assertEquals(AccessState.GRANTED, report.items.first { it.id == "notifications" }.state)
        assertEquals(AccessState.GRANTED, report.items.first { it.id == "exact_alarm" }.state)
        assertFalse(report.render("en").contains("Allow restricted settings"))
    }

    @Test
    fun fullSensitiveGrantsClearTheRestrictedHint() {
        val granted = setOf(
            Perms.READ_SMS,
            Perms.SEND_SMS,
            Perms.RECEIVE_SMS,
            Perms.READ_CALL_LOG,
        )
        val report = PermissionDiagnostics.aggregate(
            FakeProbe(
                sdkInt = 34,
                granted = granted,
                accessibilityEnabled = true,
                notificationListenerEnabled = true,
                notificationListenerConnected = true,
            ),
        )
        assertFalse(report.restrictedLikely)
        assertEquals(AccessState.GRANTED, report.items.first { it.id == "sms" }.state)
        assertEquals(AccessState.GRANTED, report.items.first { it.id == "notification_listener" }.state)
        assertEquals(AccessState.MISSING, report.items.first { it.id == "camera" }.state)
    }

    @Test
    fun enabledListenerThatIsNotConnectedAsksForAToggle() {
        val report = PermissionDiagnostics.aggregate(
            FakeProbe(
                sdkInt = 34,
                notificationListenerEnabled = true,
                notificationListenerConnected = false,
                accessibilityEnabled = true,
                granted = setOf(Perms.READ_SMS, Perms.SEND_SMS, Perms.RECEIVE_SMS, Perms.READ_CALL_LOG),
            ),
        )
        val listener = report.items.first { it.id == "notification_listener" }
        assertEquals(AccessState.NEEDS_SETTINGS, listener.state)
        assertTrue(listener.nextEn.contains("Toggle"))
    }
}

private class FakeProbe(
    override val sdkInt: Int,
    private val granted: Set<String> = emptySet(),
    override val accessibilityEnabled: Boolean = false,
    override val notificationListenerEnabled: Boolean = false,
    override val notificationListenerConnected: Boolean = false,
    override val overlayGranted: Boolean = false,
    override val allFilesGranted: Boolean = false,
    override val safTreeGranted: Boolean = false,
    override val exactAlarmGranted: Boolean = false,
    override val usageStatsGranted: Boolean = false,
    override val batteryUnrestricted: Boolean = false,
) : AccessProbe {
    override fun runtimeGranted(permission: String): Boolean = permission in granted
}
