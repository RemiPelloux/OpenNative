package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HosterAllowlistTest {
    @Test
    fun `skidrow tabs keep only mega links`() {
        val feed = "https://feeds.feedburner.com/SkidrowReloadedGames"
        assertEquals(setOf("mega.nz", "mega.io", "mega.co.nz"), HosterAllowlist.hostsFor(feed))
        val kept = HosterAllowlist.filter(
            listOf(
                "https://mega.co.nz/#!abc!key",
                "https://megaup.net/abc",
                "https://1fichier.com/foo",
                "https://www.skidrowreloaded.com/game/",
            ),
            feed,
        )
        assertEquals(listOf("https://mega.co.nz/#!abc!key"), kept)
        assertNull(HosterAllowlist.hostsFor("https://fitgirl-repacks.site/wp-json/wp/v2/posts"))
        assertEquals(
            listOf("https://mega.co.nz/#!abc!key"),
            WordpressDownloadLinks.rank(
                listOf("https://mega.co.nz/#!abc!key", "https://megaup.net/x.zip"),
                setOf("mega.nz", "mega.io", "mega.co.nz"),
            ),
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
                  <a href="https://1fichier.com/x">other</a>
                ]]></content:encoded>
              </item>
            </channel></rss>
        """.trimIndent()
        val item = RssFeedParser.parse(xml).items.single()
        assertEquals("Example Game", item.title)
        assertTrue(WordpressMetadata.linksFrom(item.extraJson).any { it.contains("mega.nz") })
        val restricted = WordpressMetadata.restrictForFeed(
            item,
            "https://feeds.feedburner.com/SkidrowReloadedGames",
        )
        assertEquals(listOf("https://mega.nz/file/abc#key"), WordpressMetadata.linksFrom(restricted.extraJson))
        assertEquals("https://www.skidrowreloaded.com/cover.jpg", item.artworkUrl)
    }
}
