package app.dastyar.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRoutesTest {
    @Test
    fun menuKeepsConnectorsAndDropsTheOldBottomBarState() {
        assertEquals(
            listOf("chat", "providers", "connectors", "routines", "settings"),
            AppRoutes.menu,
        )
        assertTrue(AppRoutes.menuRoute("connectors"))
        assertEquals("connectors", AppRoutes.restoreOrHome("connectors"))
        assertEquals("connectors", AppRoutes.restoreOrHome("connectors?x=1"))
        assertEquals("chat", AppRoutes.restoreOrHome("links"))
        assertEquals("chat", AppRoutes.restoreOrHome(null))
        assertEquals("chat", AppRoutes.restoreOrHome(""))
        assertEquals("chat", AppRoutes.restoreOrHome("missing/child"))
        assertFalse(AppRoutes.menuRoute("onboarding"))
    }

    @Test
    fun knownSecondaryScreensStayReachable() {
        assertEquals("permissions?onboarding=0", AppRoutes.restoreOrHome("permissions"))
        assertEquals("permissions?onboarding=0", AppRoutes.restoreOrHome("permissions?onboarding=1"))
        listOf("privacy", "audit", "memories", "disclosure", "onboarding", "settings").forEach { route ->
            assertEquals(route, AppRoutes.restoreOrHome(route))
        }
    }

    @Test
    fun upArrowIsOnEveryScreenExceptChatAndOnboarding() {
        assertFalse(AppRoutes.showsUp("chat"))
        assertFalse(AppRoutes.showsUp("onboarding"))
        assertFalse(AppRoutes.showsUp(null))
        listOf(
            "providers",
            "connectors",
            "routines",
            "settings",
            "permissions?onboarding=0",
            "privacy",
            "audit",
            "memories",
            "disclosure",
        ).forEach { route ->
            assertTrue(route, AppRoutes.showsUp(route))
        }
    }

    @Test
    fun systemBackClosesChromeBeforeItPopsOrFinishes() {
        assertEquals(
            BackAction.CloseDrawer,
            AppRoutes.backAction(true, false, true, true, "settings", true),
        )
        assertEquals(
            BackAction.CloseDrawer,
            AppRoutes.backAction(false, true, false, false, "chat", false),
        )
        assertEquals(
            BackAction.CloseOverlay,
            AppRoutes.backAction(false, false, true, true, "chat", false),
        )
        assertEquals(
            BackAction.CloseVoice,
            AppRoutes.backAction(false, false, true, false, "providers", true),
        )
        assertEquals(
            BackAction.Pop,
            AppRoutes.backAction(false, false, false, false, "permissions?onboarding=0", true),
        )
        assertEquals(
            BackAction.GoChat,
            AppRoutes.backAction(false, false, false, false, "connectors", false),
        )
        assertEquals(
            BackAction.FinishActivity,
            AppRoutes.backAction(false, false, false, false, "chat", false),
        )
        assertEquals(
            BackAction.Pop,
            AppRoutes.backAction(false, false, false, false, "chat", true),
        )
        assertTrue(AppRoutes.interceptsSystemBack(BackAction.GoChat))
        assertTrue(AppRoutes.interceptsSystemBack(BackAction.CloseVoice))
        assertFalse(AppRoutes.interceptsSystemBack(BackAction.Pop))
        assertFalse(AppRoutes.interceptsSystemBack(BackAction.FinishActivity))
        assertTrue(AppRoutes.voiceStageOpen(true, "Idle"))
        assertTrue(AppRoutes.voiceStageOpen(false, "Error"))
        assertFalse(AppRoutes.voiceStageOpen(false, "Idle"))
    }
}
