package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderTabBundleTest {
    @Test
    fun `round trip keeps public tab fields and strips secrets`() {
        val tab = ProviderTab(
            id = "local",
            name = "Example WordPress",
            position = 3,
            feedUrl = "https://blog.example/wp-json/wp/v2/posts",
            feedKind = FeedKind.JSON,
            credentialRef = "must-not-export",
            installTreeUri = "content://must-not-export",
            perPage = 100,
            orderBy = "date",
            order = "desc",
        )
        val json = ProviderTabCodec.encode(listOf(tab), nowMs = 1L)
        assertFalse(json.contains("must-not-export"))
        assertFalse(json.contains("content://"))
        val decoded = ProviderTabCodec.decode(json).single()
        assertEquals("Example WordPress", decoded.name)
        assertEquals("https://blog.example/wp-json/wp/v2/posts", decoded.feedUrl)
        assertEquals(100, decoded.perPage)
        assertTrue(decoded.credentialRef.isNullOrBlank())
    }

    @Test
    fun `canonicalizes wordpress query strings and rejects blocked hosts`() {
        val json = """
            {
              "schema": "opennative.provider.tabs/v1",
              "tabs": [{
                "name": "Blog",
                "feedUrl": "https://blog.example/wp-json/wp/v2/posts?per_page=100&page=1&orderby=date&order=desc&_embed=1",
                "feedKind": "JSON"
              }]
            }
        """.trimIndent()
        val decoded = ProviderTabCodec.decode(json).single()
        assertEquals("https://blog.example/wp-json/wp/v2/posts", decoded.feedUrl)
        assertFalse(decoded.feedUrl.contains("_embed"))
    }

    @Test(expected = ProviderException::class)
    fun `blocked catalog host cannot be imported`() {
        ProviderTabCodec.decode(
            """
            {
              "schema": "opennative.provider.tabs/v1",
              "tabs": [{
                "name": "Blocked",
                "feedUrl": "https://fitgirl-repacks.site/wp-json/wp/v2/posts"
              }]
            }
            """.trimIndent(),
        )
    }
}
