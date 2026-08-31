package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteCatalogParserTest {
    @Test
    fun `parses steamrip listing with size password and next page`() {
        val page = ProviderFeedParser.parse(steamripHtml(), contentType = "text/html")
        val item = page.items.single()
        assertEquals("sample-game-free-download", item.itemId)
        assertEquals("Sample Game", item.title)
        assertEquals("https://cdn.example.com/steamrip-cover.jpg", item.artworkUrl)
        assertEquals(12L * 1024 * 1024 * 1024, item.downloadSizeBytes)
        assertTrue(item.extraJson.contains("steamrip"))
        assertEquals("https://steamrip.com/page/2/", page.nextCursor)
    }

    @Test
    fun `parses ankergames and gogunlocked independently`() {
        val anker = SiteCatalogParser.parse(listing("ankergames.net", "anker-title"), SiteCatalogParser.ANKERGAMES)
        val gog = SiteCatalogParser.parse(listing("gogunlocked.com", "gog-title"), SiteCatalogParser.GOGUNLOCKED)
        assertEquals("anker-title", anker.items.single().itemId)
        assertEquals("gog-title", gog.items.single().itemId)
    }

    @Test
    fun `empty steamrip listing fails closed without affecting another adapter`() {
        try {
            SiteCatalogParser.parse("<html>steamrip empty</html>", SiteCatalogParser.STEAMRIP)
            throw AssertionError("expected failure")
        } catch (error: ProviderException) {
            assertEquals(ProviderErrorCode.MALFORMED_RESPONSE, error.code)
        }
        val gog = SiteCatalogParser.parse(listing("gogunlocked.com", "still-good"), SiteCatalogParser.GOGUNLOCKED)
        assertEquals("still-good", gog.items.single().itemId)
    }

    @Test
    fun `skidrow html is not claimed by steamrip`() {
        val spec = SiteCatalogParser.matchingSpec(
            """<!doctype html><html>skidrowreloaded</html>""",
            "text/html",
        )
        assertEquals(null, spec)
    }

    private fun steamripHtml(): String = """
        <!doctype html>
        <html><body>
        <h2><a href="https://steamrip.com/sample-game-free-download/">Sample Game</a></h2>
        <p><img src="https://cdn.example.com/steamrip-cover.jpg" /></p>
        <p>Size: 12 GB</p>
        <p>Password: steamrip</p>
        <a href="https://steamrip.com/page/2/">Next</a>
        </body></html>
    """.trimIndent()

    private fun listing(host: String, slug: String): String = """
        <!doctype html>
        <html><body>
        <h2><a href="https://$host/$slug/">Title $slug</a></h2>
        </body></html>
    """.trimIndent()
}
