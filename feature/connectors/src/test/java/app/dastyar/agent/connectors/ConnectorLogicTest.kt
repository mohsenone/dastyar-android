package app.dastyar.agent.connectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorLogicTest {
    @Test
    fun mcpNamesStayStableAndUnique() {
        assertEquals("mcp_home_list_files", McpNames.toolName("Home", "list files"))
        val used = mutableSetOf("mcp_home_list_files")
        val second = McpNames.unique(McpNames.toolName("Home", "list files"), used)
        assertTrue(second.startsWith("mcp_home_list_files_"))
        assertTrue(second.length <= 64)
    }

    @Test
    fun oauthRedirectMustMatchTheManifest() {
        assertNull(OAuthRedirect.problem("app.dastyar.agent://oauth2redirect"))
        assertTrue(OAuthRedirect.problem("https://example.com/callback")!!.contains("scheme"))
        assertTrue(OAuthRedirect.problem("app.dastyar.agent://other")!!.contains("host"))
    }

    @Test
    fun webhookAllowlistBlocksCleartextAndUnknownHosts() {
        assertNull(WebhookPolicy.rejection("https://api.example.com/hook", ""))
        assertTrue(WebhookPolicy.rejection("http://api.example.com/hook", "")!!.contains("allowlist"))
        assertNull(WebhookPolicy.rejection("http://api.example.com/hook", "example.com"))
        assertTrue(WebhookPolicy.rejection("https://evil.test/hook", "example.com")!!.contains("allowlist"))
        assertNull(WebhookPolicy.rejection("https://api.example.com/hook", "example.com"))
    }

    @Test
    fun rssAndWeatherUrlsAreBuiltWithoutInventingAKey() {
        assertEquals("https://example.com/feed", RssUrls.normalize(" https://example.com/feed "))
        assertNull(RssUrls.normalize("ftp://example.com/feed"))
        assertTrue(WeatherUrls.geocode("Tehran").startsWith("https://geocoding-api.open-meteo.com/"))
        assertTrue(WeatherUrls.forecast(35.7, 51.4).contains("latitude=35.7"))
        assertTrue(WeatherUrls.openWeather("Tehran", "k").contains("appid=k"))
    }

    @Test
    fun sseExtractsJsonDataLines() {
        val raw = "event: message\ndata: {\"id\":1}\n\ndata: [DONE]\n"
        assertEquals(listOf("{\"id\":1}"), SsePayload.jsonEvents(raw))
    }

    @Test
    fun statusIsNeverConnectedWithoutARealSignal() {
        assertEquals(
            LinkStatus.OFF,
            ConnectorStatus.of(ConnectorIds.GITHUB, false, true, "", "open-meteo", "", 0, false),
        )
        assertEquals(
            LinkStatus.NEEDS_AUTH,
            ConnectorStatus.of(ConnectorIds.GITHUB, true, false, "", "open-meteo", "", 0, false),
        )
        assertEquals(
            LinkStatus.CONNECTED,
            ConnectorStatus.of(ConnectorIds.GITHUB, true, true, "", "open-meteo", "", 0, false),
        )
        assertEquals(
            LinkStatus.CONNECTED,
            ConnectorStatus.of(ConnectorIds.WEATHER, true, false, "", "open-meteo", "", 0, false),
        )
        assertEquals(
            LinkStatus.NEEDS_AUTH,
            ConnectorStatus.of(ConnectorIds.WEATHER, true, false, "", "openweather", "", 0, false),
        )
        assertEquals(
            LinkStatus.NEEDS_AUTH,
            ConnectorStatus.of(ConnectorIds.MCP, true, true, "", "open-meteo", "", 0, false),
        )
        assertEquals(
            LinkStatus.ERROR,
            ConnectorStatus.of(ConnectorIds.SHIZUKU, true, false, "unauthorized", "open-meteo", "", 0, false),
        )
    }

    @Test
    fun rowLookupDoesNotThrowWhenTheSnapshotIsEmptyOrMissingAnId() {
        ConnectorIds.catalog.forEach { id ->
            assertNull(connectorRow(emptyList(), id))
        }
        val missingGithub = ConnectorIds.catalog
            .filter { it != ConnectorIds.GITHUB }
            .map { ConnectorRow(it, LinkStatus.OFF, "") }
        assertNull(connectorRow(missingGithub, ConnectorIds.GITHUB))
        assertEquals(ConnectorIds.TELEGRAM, connectorRow(missingGithub, ConnectorIds.TELEGRAM)?.id)
        val one = listOf(ConnectorRow(ConnectorIds.RSS, LinkStatus.NEEDS_AUTH, ""))
        ConnectorIds.catalog.filter { it != ConnectorIds.RSS }.forEach { id ->
            assertNull(connectorRow(one, id))
        }
    }

    @Test
    fun signingSha1IsADashWhenTheCertificateIsMissing() {
        assertEquals("—", SigningSha1.format(null))
        assertEquals("—", SigningSha1.format(byteArrayOf()))
        val formatted = SigningSha1.format("dastyar".toByteArray())
        assertEquals(59, formatted.length)
        assertTrue(formatted.all { it.isDigit() || it in 'A'..'F' || it == ':' })
    }

    @Test
    fun googleScopesStayLimitedUnlessTheUserOptsIn() {
        val base = GoogleScopes.request(false)
        assertTrue(GoogleScopes.GMAIL_SEND in base)
        assertTrue(GoogleScopes.CALENDAR in base)
        assertTrue(GoogleScopes.DRIVE !in base)
        assertTrue(GoogleScopes.DRIVE in GoogleScopes.request(true))
    }
}
