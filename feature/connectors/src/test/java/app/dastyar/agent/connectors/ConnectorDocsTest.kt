package app.dastyar.agent.connectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorDocsTest {
    @Test
    fun emptyAndMissingKeysStayAWritableSnapshot() {
        assertTrue(ConnectorDocs.read(null).recognized)
        assertTrue(ConnectorDocs.read("").recognized)
        assertTrue(ConnectorDocs.read("   ").recognized)
        assertEquals(ConnectorSnapshot(), ConnectorDocs.read(null).snapshot)

        val partial = ConnectorDocs.read("""{"googleClientId":"abc"}""")
        assertTrue(partial.recognized)
        assertEquals("abc", partial.snapshot.googleClientId)
        assertEquals(OAuthRedirect.DEFAULT, partial.snapshot.googleRedirect)
        assertEquals("tavily", partial.snapshot.searchKind)
        assertEquals("open-meteo", partial.snapshot.weatherKind)
        assertTrue(partial.snapshot.enabled.isEmpty())
        assertTrue(partial.snapshot.mcp.isEmpty())
    }

    @Test
    fun corruptTextAndAJsonArrayAreNotTreatedAsAnEmptyDocument() {
        listOf("not-json", "{", "[]", "[1,2]", "\"hello\"", "12", "null", "true").forEach { raw ->
            val parsed = ConnectorDocs.read(raw)
            assertFalse(raw, parsed.recognized)
            assertEquals(ConnectorSnapshot(), parsed.snapshot)
        }
    }

    @Test
    fun olderAndOddFieldShapesDoNotThrow() {
        val raw = """
            {
              "enabled": {"google": true, "github": false, "rss": 1, "broken": "no"},
              "googleExtraScopes": "true",
              "googleRedirect": 15,
              "mcp": {"url": "nope"},
              "rss": "https://example.com/feed",
              "notes": ["skip"],
              "searchKind": true,
              "webhookHeader": null
            }
        """.trimIndent()
        val snap = ConnectorDocs.read(raw)
        assertTrue(snap.recognized)
        assertEquals(setOf("google", "rss"), snap.snapshot.enabled)
        assertTrue(snap.snapshot.googleExtraScopes)
        assertEquals("15", snap.snapshot.googleRedirect)
        assertTrue(snap.snapshot.mcp.isEmpty())
        assertTrue(snap.snapshot.rss.isEmpty())
        assertTrue(snap.snapshot.notes.isEmpty())
        assertEquals("true", snap.snapshot.searchKind)
        assertEquals("Authorization", snap.snapshot.webhookHeader)
    }

    @Test
    fun enabledStringAndRoundTripKeepTheHistoricalShape() {
        val fromString = ConnectorDocs.read("""{"enabled":"google, rss\nweather"}""")
        assertEquals(setOf("google", "rss", "weather"), fromString.snapshot.enabled)

        val original = ConnectorSnapshot(
            enabled = setOf(ConnectorIds.GOOGLE, ConnectorIds.RSS),
            googleClientId = "client",
            googleRedirect = OAuthRedirect.DEFAULT,
            googleExtraScopes = true,
            mcp = listOf(McpServer("home", "Home", "https://example.com/mcp", "sse")),
            rss = listOf("https://example.com/feed"),
            searchKind = "searxng",
            searxngBase = "https://search.example",
            weatherKind = "openweather",
            webhookUrl = "https://hooks.example/d",
            webhookHeader = "X-Token",
            notes = mapOf(ConnectorIds.GITHUB to "ok"),
        )
        val again = ConnectorDocs.read(ConnectorDocs.write(original))
        assertTrue(again.recognized)
        assertEquals(original.enabled, again.snapshot.enabled)
        assertEquals(original.mcp, again.snapshot.mcp)
        assertEquals(original.rss, again.snapshot.rss)
        assertEquals(original.notes, again.snapshot.notes)
        assertEquals(original.googleClientId, again.snapshot.googleClientId)
        assertEquals(original.webhookUrl, again.snapshot.webhookUrl)
        assertTrue(again.snapshot.googleExtraScopes)
    }
}
