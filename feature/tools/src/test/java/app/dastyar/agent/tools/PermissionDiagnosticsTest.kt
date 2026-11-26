package app.dastyar.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PermissionDiagnosticsTest {
    @Before
    fun clearMemory() = PermissionMemory.reset()

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

    @Test
    fun notificationsAreSkippedBeforeApi33() {
        assertEquals(AccessState.GRANTED, PermissionDiagnostics.aggregate(FakeProbe(sdkInt = 32)).items.first { it.id == "notifications" }.state)
        assertEquals(AccessState.MISSING, PermissionDiagnostics.aggregate(FakeProbe(sdkInt = 33)).items.first { it.id == "notifications" }.state)
    }

    @Test
    fun backgroundLocationWaitsForForeground() {
        assertFalse(LocationAccess.canRequestBackground(34, foreground = false))
        assertTrue(LocationAccess.canRequestBackground(34, foreground = true))
        assertFalse(LocationAccess.canRequestBackground(28, foreground = true))
        val missing = PermissionDiagnostics.aggregate(FakeProbe(sdkInt = 34))
        assertEquals(AccessState.MISSING, missing.items.first { it.id == "background_location" }.state)
        val foreground = PermissionDiagnostics.aggregate(
            FakeProbe(sdkInt = 34, granted = setOf(Perms.ACCESS_FINE_LOCATION)),
        )
        assertEquals(AccessState.GRANTED, foreground.items.first { it.id == "location" }.state)
        assertEquals(AccessState.NEEDS_SETTINGS, foreground.items.first { it.id == "background_location" }.state)
    }

    @Test
    fun partialPhotoAccessCountsOnApi34() {
        val partial = PermissionDiagnostics.aggregate(
            FakeProbe(sdkInt = 34, granted = setOf(Perms.READ_MEDIA_VISUAL_USER_SELECTED)),
        )
        assertEquals(AccessState.GRANTED, partial.items.first { it.id == "media" }.state)
        assertEquals(AccessState.MISSING, PermissionDiagnostics.aggregate(FakeProbe(sdkInt = 33)).items.first { it.id == "media" }.state)
        assertTrue(MediaAccess.requestPermissions(34).contains(Perms.READ_MEDIA_VISUAL_USER_SELECTED))
        assertFalse(MediaAccess.requestPermissions(33).contains(Perms.READ_MEDIA_VISUAL_USER_SELECTED))
    }

    @Test
    fun dontAskAgainRoutesToSettingsInsteadOfAnotherDialog() {
        val report = PermissionDiagnostics.aggregate(
            FakeProbe(sdkInt = 30, permanent = setOf(Perms.CAMERA)),
        )
        val camera = report.items.first { it.id == "camera" }
        assertEquals(AccessState.NEEDS_SETTINGS, camera.state)
        assertEquals(PermissionTargets.APP_INFO, camera.target)
        assertTrue(camera.nextEn.contains("will not show"))
    }

    @Test
    fun fixQueueSkipsGrantedItemsAndDoesNotNagAllFilesOrShizuku() {
        val report = PermissionDiagnostics.aggregate(
            FakeProbe(sdkInt = 34, granted = setOf(Perms.CAMERA), shizuku = ShizukuStatusHook.MISSING),
        )
        val queue = PermissionPlanner.fixQueue(report.items)
        assertTrue(queue.none { it.id == "camera" || it.id == "all_files" || it.id == "shizuku" })
        assertTrue(queue.any { it.id == "sms" })
        val shizuku = report.items.first { it.id == "shizuku" }
        assertEquals(AccessState.MISSING, shizuku.state)
        assertTrue(shizuku.nextEn.contains("not installed"))
    }

    @Test
    fun denialNamesTheMissingPermission() {
        val text = PermissionMessages.denied("en", listOf(Perms.READ_SMS), 28)
        assertTrue(text.contains("READ_SMS"))
        assertTrue(text.contains("Fix"))
        assertFalse(text.contains("Allow restricted settings"))
        val fa = PermissionMessages.denied("fa", listOf(Perms.RECORD_AUDIO), 34)
        assertTrue(fa.contains("RECORD_AUDIO"))
        assertTrue(fa.contains("رفع"))
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
    override val shizuku: String = ShizukuStatusHook.UNKNOWN,
    private val permanent: Set<String> = emptySet(),
) : AccessProbe {
    override fun runtimeGranted(permission: String): Boolean = permission in granted
    override fun permanentlyDenied(permission: String): Boolean = permission in permanent
}
