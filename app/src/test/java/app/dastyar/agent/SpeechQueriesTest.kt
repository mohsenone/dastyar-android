package app.dastyar.agent

import app.dastyar.agent.voice.SpeechVisibility
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechQueriesTest {
    @Test
    fun manifestCanSeeSpeechEnginesWithoutABlanketQuery() {
        val xml = File("src/main/AndroidManifest.xml").readText()
        val queries = xml.substringAfter("<queries>").substringBefore("</queries>")
        SpeechVisibility.actions.forEach { action ->
            assertTrue("missing $action", queries.contains(action))
        }
        SpeechVisibility.packages.forEach { pkg ->
            assertTrue("missing $pkg", queries.contains(pkg))
        }
        assertTrue(queries.contains("android.speech.RecognitionService"))
        assertFalse(queries.contains("android.permission.QUERY_ALL_PACKAGES"))
    }
}
