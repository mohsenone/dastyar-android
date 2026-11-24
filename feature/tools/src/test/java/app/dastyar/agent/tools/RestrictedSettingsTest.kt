package app.dastyar.agent.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictedSettingsTest {
    @Test
    fun appliesFromAndroid13() {
        assertFalse(RestrictedSettings.applies(32))
        assertTrue(RestrictedSettings.applies(33))
        assertTrue(RestrictedSettings.applies(35))
    }

    @Test
    fun smsAndCallLogAreTheRestrictedRuntimeGroup() {
        assertTrue(RestrictedSettings.touchesRuntime(listOf(Perms.READ_SMS)))
        assertTrue(RestrictedSettings.touchesRuntime(listOf(Perms.SEND_SMS, Perms.CAMERA)))
        assertTrue(RestrictedSettings.touchesRuntime(listOf(Perms.READ_CALL_LOG)))
        assertTrue(RestrictedSettings.touchesRuntime(listOf(Perms.RECEIVE_SMS, Perms.RECEIVE_MMS, Perms.RECEIVE_WAP_PUSH)))
        assertFalse(RestrictedSettings.touchesRuntime(listOf(Perms.CAMERA, Perms.CALL_PHONE)))
        assertFalse(RestrictedSettings.touchesRuntime(emptyList()))
    }

    @Test
    fun likelyOnlyWhenAndroid13AndARelevantGrantIsMissing() {
        assertFalse(RestrictedSettings.likely(32, smsGranted = false, callLogGranted = false, accessibilityOn = false, listenerOn = false))
        assertTrue(RestrictedSettings.likely(33, smsGranted = false, callLogGranted = true, accessibilityOn = true, listenerOn = true))
        assertTrue(RestrictedSettings.likely(34, smsGranted = true, callLogGranted = true, accessibilityOn = false, listenerOn = true))
        assertFalse(RestrictedSettings.likely(34, smsGranted = true, callLogGranted = true, accessibilityOn = true, listenerOn = true))
    }

    @Test
    fun guideNamesTheManualStepsInBothLanguages() {
        val fa = RestrictedSettings.guideFa()
        val en = RestrictedSettings.guideEn()
        assertTrue(fa.contains("اجازه به تنظیمات محدود"))
        assertTrue(fa.contains("⋮"))
        assertTrue(fa.contains("اندروید ۱۵"))
        assertTrue(en.contains("Allow restricted settings"))
        assertTrue(en.contains("Android 15"))
        assertTrue(RestrictedSettings.guide("fa").contains("سه نقطه"))
        assertTrue(RestrictedSettings.guide("en").contains("Open app info"))
    }

    @Test
    fun denialMentionsRestrictedSettingsOnlyForThatGroupOnAndroid13() {
        val sms = PermissionMessages.denied("en", listOf(Perms.READ_SMS), 34)
        assertTrue(sms.contains("Permission denied."))
        assertTrue(sms.contains("Allow restricted settings"))
        val fa = PermissionMessages.denied("fa", listOf(Perms.READ_CALL_LOG), 33)
        assertTrue(fa.contains("اجازه داده نشد."))
        assertTrue(fa.contains("اجازه به تنظیمات محدود"))
        val camera = PermissionMessages.denied("en", listOf(Perms.CAMERA), 34)
        assertFalse(camera.contains("Allow restricted settings"))
        val old = PermissionMessages.denied("en", listOf(Perms.SEND_SMS), 28)
        assertFalse(old.contains("Allow restricted settings"))
    }

    @Test
    fun componentListMatchesFlattenedServiceNames() {
        val flat = "com.other/.Other:app.dastyar.agent/app.dastyar.agent.tools.DastyarAccessibilityService"
        assertTrue(ComponentLists.listed(flat, "app.dastyar.agent", PermissionDiagnostics.A11Y_CLASS))
        assertTrue(ComponentLists.listed("app.dastyar.agent/.DastyarNotificationListener", "app.dastyar.agent", PermissionDiagnostics.LISTENER_CLASS))
        assertFalse(ComponentLists.listed(flat, "app.dastyar.agent", PermissionDiagnostics.LISTENER_CLASS))
        assertFalse(ComponentLists.listed(null, "app.dastyar.agent", PermissionDiagnostics.A11Y_CLASS))
    }
}
