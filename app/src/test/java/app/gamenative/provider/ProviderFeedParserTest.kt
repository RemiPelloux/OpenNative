package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFeedParserTest {
    @Test
    fun `parses versioned json envelope`() {
        val page = ProviderFeedParser.parse(
            """
            {
              "version": 1,
              "nextCursor": "abc",
              "items": [
                {
                  "id": "game-1",
                  "title": "Example",
                  "link": "https://example.com/file.zip",
                  "size": 1024,
                  "architecture": "x64"
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(1, page.items.size)
        assertEquals("game-1", page.items.single().itemId)
        assertEquals("x64", page.items.single().architecture)
        assertEquals("abc", page.nextCursor)
    }

    @Test
    fun `parses rss items and enclosure`() {
        val page = ProviderFeedParser.parse(
            """
            <rss version="2.0">
              <channel>
                <title>Catalog</title>
                <item>
                  <title>Portable Build</title>
                  <guid>item-1</guid>
                  <link>https://example.com/page</link>
                  <enclosure url="https://example.com/game.zip" length="2048" type="application/zip" />
                </item>
              </channel>
            </rss>
            """.trimIndent(),
            contentType = "application/rss+xml",
        )
        assertEquals(1, page.items.size)
        assertEquals("Portable Build", page.items.single().title)
        assertEquals("https://example.com/page", page.items.single().link)
        assertEquals(2048L, page.items.single().downloadSizeBytes)
    }

    @Test
    fun `parses atom entries`() {
        val page = ProviderFeedParser.parse(
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Catalog</title>
              <entry>
                <id>atom-1</id>
                <title>Atom Game</title>
                <link href="https://example.com/atom.zip" rel="enclosure" />
              </entry>
            </feed>
            """.trimIndent(),
        )
        assertEquals("Atom Game", page.items.single().title)
        assertEquals("https://example.com/atom.zip", page.items.single().link)
    }

    @Test
    fun `rejects unknown json version`() {
        try {
            JsonFeedParser.parse("""{"version":2,"items":[]}""")
            throw AssertionError("expected failure")
        } catch (error: ProviderException) {
            assertEquals(ProviderErrorCode.MALFORMED_RESPONSE, error.code)
        }
    }

    @Test
    fun `parses wordpress rest arrays without reading html content`() {
        val page = ProviderFeedParser.parse(
            """
            [
              {
                "id": 9,
                "link": "https://example.com/hello",
                "title": { "rendered": "Hello" },
                "excerpt": { "rendered": "<p>Hi</p>" },
                "content": { "rendered": "<p><img src=\"https://cdn.example.com/cover.jpg\" />magnet:not-used</p>" }
              }
            ]
            """.trimIndent(),
        )
        val item = page.items.single()
        assertEquals("9", item.itemId)
        assertEquals("Hello", item.title)
        assertEquals("https://example.com/hello", item.link)
        assertEquals("https://cdn.example.com/cover.jpg", item.artworkUrl)
        assertEquals("Hi", item.description)
        assertEquals("{}", item.extraJson)
    }

    @Test
    fun `decodes wordpress title entities for cuphead`() {
        val page = ProviderFeedParser.parse(
            """
            [
              {
                "id": 5361,
                "link": "https://fitgirl-repacks.site/cuphead/",
                "title": { "rendered": "Cuphead: Game &#038; Soundtrack Bundle &#8211; v1.3.9 + DLC + Bonus OSTs" },
                "excerpt": { "rendered": "<p>Arcade &amp; run and gun</p>" }
              }
            ]
            """.trimIndent(),
        )
        val item = page.items.single()
        assertEquals("Cuphead: Game & Soundtrack Bundle – v1.3.9 + DLC + Bonus OSTs", item.title)
        assertEquals("Arcade & run and gun", item.description)
    }

    @Test
    fun `skips wordpress updates digest posts`() {
        val page = ProviderFeedParser.parse(
            """
            [
              {
                "id": 1,
                "link": "https://fitgirl-repacks.site/updates-digest-for-july-19-2026/",
                "title": { "rendered": "Updates Digest for July 19, 2026" }
              },
              {
                "id": 5361,
                "link": "https://fitgirl-repacks.site/cuphead/",
                "title": { "rendered": "Cuphead" }
              }
            ]
            """.trimIndent(),
        )
        assertEquals(listOf("5361"), page.items.map { it.itemId })
    }

    @Test
    fun `parses skidrow html listing posts`() {
        val page = ProviderFeedParser.parse(
            """
            <!doctype html>
            <html><body>
            <div class="post type-post">
              <h2><a href="https://www.skidrowreloaded.com/grim-trials-tenoke/">Grim Trials-TENOKE</a></h2>
              <div class="meta">Posted August 21, 2026 in PC GAMES</div>
              <div class="post-excerpt">
                <p><a href="https://www.skidrowreloaded.com/grim-trials-tenoke/">
                  <img src="https://cdn.example.com/grim.jpg" />
                </a></p>
              </div>
            </div>
            <div class="post type-post">
              <h2><a href="https://www.skidrowreloaded.com/slayblade-tenoke/">Slayblade-TENOKE</a></h2>
              <div class="meta">Posted August 20, 2026 in PC GAMES</div>
            </div>
            </body></html>
            """.trimIndent(),
            contentType = "text/html; charset=UTF-8",
        )
        assertEquals(listOf("grim-trials-tenoke", "slayblade-tenoke"), page.items.map { it.itemId })
        assertEquals("Grim Trials-TENOKE", page.items.first().title)
        assertEquals("https://cdn.example.com/grim.jpg", page.items.first().artworkUrl)
        assertTrue(page.items.first().publishedAtEpochMs > 0L)
    }

    @Test
    fun `limits page size`() {
        val items = (1..120).joinToString(",") { """{"id":"$it","title":"T$it","link":"https://example.com/$it"}""" }
        val page = ProviderFeedParser.parse("""{"version":1,"items":[$items]}""")
        assertEquals(ProviderUrlPolicy.PAGE_SIZE, page.items.size)
        assertTrue(page.items.size <= 100)
    }
}
