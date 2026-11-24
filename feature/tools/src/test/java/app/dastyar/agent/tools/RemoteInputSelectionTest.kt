package app.dastyar.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteInputSelectionTest {
    @Test
    fun prefersStandardFreeFormOverWearable() {
        val picked = RemoteInputSelection.pick(
            listOf(
                ReplyAction("wearable", 0, 0, "wear", freeForm = true),
                ReplyAction("standard", 1, 0, "choices", freeForm = false),
                ReplyAction("standard", 2, 1, "reply", freeForm = true),
            ),
        )
        assertEquals("standard", picked?.source)
        assertEquals(2, picked?.actionIndex)
        assertEquals(1, picked?.inputIndex)
        assertEquals("reply", picked?.resultKey)
    }

    @Test
    fun usesWearableFreeFormWhenTheNotificationHasNone() {
        val picked = RemoteInputSelection.pick(
            listOf(
                ReplyAction("standard", 0, 0, "quick", freeForm = false),
                ReplyAction("wearable", 0, 0, "wear_reply", freeForm = true),
            ),
        )
        assertEquals("wearable", picked?.source)
        assertEquals("wear_reply", picked?.resultKey)
    }

    @Test
    fun ignoresBlankKeysAndChoiceOnlyActions() {
        assertNull(
            RemoteInputSelection.pick(
                listOf(
                    ReplyAction("standard", 0, 0, "", freeForm = true),
                    ReplyAction("standard", 1, 0, "only-choices", freeForm = false),
                ),
            ),
        )
        assertNull(RemoteInputSelection.pick(emptyList()))
    }
}
