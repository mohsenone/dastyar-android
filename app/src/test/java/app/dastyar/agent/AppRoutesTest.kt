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
}
