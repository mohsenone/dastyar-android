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
        assertTrue(fa.contains("سه نقطه"))
        assertTrue(fa.contains("اندروید ۱۵"))
        assertFalse(fa.contains("\u22EE"))
        assertTrue(en.contains("Allow restricted settings"))
        assertTrue(en.contains("three-dot menu"))
        assertTrue(en.contains("Android 15"))
        assertFalse(en.contains("\u22EE"))
        assertTrue(RestrictedSettings.guide("fa").contains("سه نقطه"))
        assertTrue(RestrictedSettings.guide("en").contains("Open app info"))
        assertFalse(RestrictedSettings.guide("en").contains("\u22EE"))
        assertFalse(RestrictedSettings.guide("fa").contains("\u22EE"))
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
    fun vendorNotesStayHonestInBothLanguages() {
        val en = RestrictedSettings.vendorNotes("en")
        val fa = RestrictedSettings.vendorNotes("fa")
        assertTrue(en.contains("Samsung"))
        assertTrue(en.contains("three-dot menu"))
        assertTrue(en.contains("Xiaomi"))
        assertTrue(en.contains("Huawei"))
        assertTrue(en.contains("not a promise"))
        assertFalse(en.contains("\u22EE"))
        assertTrue(fa.contains("سامسونگ"))
        assertTrue(fa.contains("سه نقطه"))
        assertTrue(fa.contains("شیائومی"))
        assertTrue(fa.contains("هواوی"))
        assertTrue(fa.contains("وعده نیست"))
        assertFalse(fa.contains("\u22EE"))
    }

    @Test
    fun componentListMatchesFlattenedServiceNames() {
        val flat = "com.other/.Other:app.dastyar.agent/app.dastyar.agent.tools.DastyarAccessibilityService"
        assertTrue(ComponentLists.listed(flat, "app.dastyar.agent", PermissionDiagnostics.A11Y_CLASS))
        assertTrue(ComponentLists.listed("app.dastyar.agent/.DastyarNotificationListener", "app.dastyar.agent", PermissionDiagnostics.LISTENER_CLASS))
        assertFalse(ComponentLists.listed(flat, "app.dastyar.agent", PermissionDiagnostics.LISTENER_CLASS))
        assertFalse(ComponentLists.listed(null, "app.dastyar.agent", PermissionDiagnostics.A11Y_CLASS))
        assertFalse(ComponentLists.listed("", "app.dastyar.agent", PermissionDiagnostics.LISTENER_CLASS))
        assertFalse(ComponentLists.listed("   ", "app.dastyar.agent", PermissionDiagnostics.LISTENER_CLASS))
    }

    @Test
    fun componentListMatchesApi26To35FlattenedNestedClassNames() {
        val pkg = "app.dastyar.agent"
        val listener = PermissionDiagnostics.LISTENER_CLASS
        val a11y = PermissionDiagnostics.A11Y_CLASS
        assertTrue(ComponentLists.listed("$pkg/$listener", pkg, listener))
        assertTrue(ComponentLists.listed("$pkg/.tools.DastyarNotificationListener", pkg, listener))
        assertTrue(ComponentLists.listed("$pkg/.tools.DastyarAccessibilityService", pkg, a11y))
        val mixed = "com.other/.Other:$pkg/.tools.DastyarNotificationListener:$pkg/$a11y"
        assertTrue(ComponentLists.listed(mixed, pkg, listener))
        assertTrue(ComponentLists.listed(mixed, pkg, a11y))
        assertFalse(ComponentLists.listed("com.other/.tools.DastyarNotificationListener", pkg, listener))
        assertFalse(ComponentLists.listed("$pkg/.tools.OtherService", pkg, listener))
    }
}
