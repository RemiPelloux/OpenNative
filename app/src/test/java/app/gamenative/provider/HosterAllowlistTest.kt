package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HosterAllowlistTest {
    @Test
    fun `skidrow tabs keep only 1fichier links`() {
        val feed = "https://feeds.feedburner.com/SkidrowReloadedGames"
        assertEquals(setOf("1fichier.com"), HosterAllowlist.hostsFor(feed))
        val kept = HosterAllowlist.filter(
            listOf(
                "https://mega.co.nz/#!abc!key",
                "https://megaup.net/abc",
                "https://1fichier.com/?abcd1234",
                "https://www.1fichier.com/?efgh5678",
                "https://www.skidrowreloaded.com/game/",
            ),
            feed,
        )
        assertEquals(
            listOf("https://1fichier.com/?abcd1234", "https://www.1fichier.com/?efgh5678"),
            kept,
        )
        assertNull(HosterAllowlist.hostsFor("https://fitgirl-repacks.site/wp-json/wp/v2/posts"))
        assertTrue(ProviderTabPolicy.extractOnly(feed))
        assertTrue(!ProviderTabPolicy.extractOnly("https://fitgirl-repacks.site/wp-json/wp/v2/posts"))
        assertEquals(
            listOf("https://1fichier.com/?abcd1234"),
            WordpressDownloadLinks.rank(
                listOf("https://mega.co.nz/#!abc!key", "https://1fichier.com/?abcd1234"),
                setOf("1fichier.com"),
            ),
        )
        assertEquals(
            "",
            ProviderDownloadRoute.magnetFor(feed, "magnet:?xt=urn:btih:abc") { "magnet:?xt=urn:btih:scraped" },
        )
        assertEquals(
            "magnet:?xt=urn:btih:abc",
            ProviderDownloadRoute.magnetFor(
                "https://fitgirl-repacks.site/wp-json/wp/v2/posts",
                "magnet:?xt=urn:btih:abc",
            ) { "magnet:?xt=urn:btih:scraped" },
        )
    }

    @Test
    fun `rss stores hoster links from content encoded`() {
        val xml = """
            <rss><channel>
              <item>
                <title>Example Game</title>
                <guid>https://www.skidrowreloaded.com/example/</guid>
                <link>https://www.skidrowreloaded.com/example/</link>
                <description>Short</description>
                <content:encoded><![CDATA[
                  <img src="https://www.skidrowreloaded.com/cover.jpg" />
                  <a href="https://mega.nz/file/abc#key">MEGA</a>
                  <a href="https://1fichier.com/?abcd1234">1fichier</a>
                ]]></content:encoded>
              </item>
            </channel></rss>
        """.trimIndent()
        val item = RssFeedParser.parse(xml).items.single()
        assertEquals("Example Game", item.title)
        assertTrue(WordpressMetadata.linksFrom(item.extraJson).any { it.contains("1fichier.com") })
        val restricted = WordpressMetadata.restrictForFeed(
            item,
            "https://feeds.feedburner.com/SkidrowReloadedGames",
        )
        assertEquals(listOf("https://1fichier.com/?abcd1234"), WordpressMetadata.linksFrom(restricted.extraJson))
        assertEquals("https://www.skidrowreloaded.com/cover.jpg", item.artworkUrl)
    }
}
