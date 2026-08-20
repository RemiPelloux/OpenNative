package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordpressMetadataTest {
    @Test
    fun `parses repack and original sizes`() {
        val (download, uncompressed) = WordpressMetadata.sizes(
            "Original Size: 3.1 GB Repack Size: 2.3 GB",
        )
        assertEquals((2.3 * 1_073_741_824.0).toLong(), download)
        assertEquals((3.1 * 1_073_741_824.0).toLong(), uncompressed)
    }

    @Test
    fun `parses fitgirl html and ranged repack size`() {
        val (download, uncompressed) = WordpressMetadata.sizes(
            "Original Size: <strong>6.9 GB</strong> Repack Size: <strong>3.6/4 GB</strong>",
        )
        assertEquals((4.0 * 1_073_741_824.0).toLong(), download)
        assertEquals((6.9 * 1_073_741_824.0).toLong(), uncompressed)
    }

    @Test
    fun `keeps https hoster links and skips magnets and images`() {
        val links = WordpressMetadata.httpsLinks(
            """
            <a href="magnet:?xt=urn:btih:abc">x</a>
            <img src="https://i8.imageban.ru/out/cover.jpg" />
            <a href="https://datanodes.to/file/part1.rar">part</a>
            """.trimIndent(),
        )
        assertEquals(listOf("https://datanodes.to/file/part1.rar"), links)
        assertFalse(links.any { it.contains("magnet") })
    }

    @Test
    fun `stores the first magnet from post html`() {
        val magnet = WordpressMagnets.first(
            """<a href="magnet:?xt=urn:btih:abc123&amp;dn=Game">torrent</a>""",
        )
        assertEquals("magnet:?xt=urn:btih:abc123&dn=Game", magnet)
        val extra = WordpressMetadata.extraJson(emptyList(), magnet)
        val item = ProviderFeedItem(itemId = "1", title = "Game", link = "https://example.com/g", extraJson = extra)
        assertEquals(magnet, WordpressMetadata.magnetOf(item))
    }

    @Test
    fun `preferred link does not fall back to a blog post url`() {
        val item = ProviderFeedItem(
            itemId = "1",
            title = "Game",
            link = "https://fitgirl-repacks.site/cuphead/",
            extraJson = "{}",
        )
        assertEquals("", WordpressMetadata.preferredLink(item))
    }

    @Test
    fun `ranks file hosters above tag and tracker pages`() {
        val links = WordpressMetadata.httpsLinks(
            """
            <a href="https://fitgirl-repacks.site/tag/arcade/">tag</a>
            <a href="https://1337x.to/torrent/1/">tracker</a>
            <a href="https://www.internetdownloadmanager.com/">idm</a>
            <a href="https://datanodes.to/abc/Cuphead_--_fitgirl-repacks.site_--_.part2.rar">p2</a>
            <a href="https://datanodes.to/abc/Cuphead_--_fitgirl-repacks.site_--_.part1.rar">p1</a>
            """.trimIndent(),
        )
        assertEquals(
            "https://datanodes.to/abc/Cuphead_--_fitgirl-repacks.site_--_.part1.rar",
            links.first(),
        )
        assertFalse(links.any { it.contains("1337x") || it.contains("/tag/") })
        assertFalse(links.any { it.contains("internetdownloadmanager") })
    }

    @Test
    fun `preferred link uses stored hoster and skips cached tag pages`() {
        val extra = WordpressMetadata.extraJson(
            listOf(
                "https://fitgirl-repacks.site/tag/arcade/",
                "https://datanodes.to/file/part1.rar",
            ),
        )
        val item = ProviderFeedItem(
            itemId = "1",
            title = "Game",
            link = "https://fitgirl-repacks.site/cuphead/",
            extraJson = extra,
        )
        assertEquals("https://datanodes.to/file/part1.rar", WordpressMetadata.preferredLink(item))
    }

    @Test
    fun `folder name stays filesystem safe`() {
        assertEquals("twisted-tower-v1-0-3", ProviderInstallHandler.folderName("Twisted Tower – v1.0.3"))
        assertTrue(ProviderInstallHandler.folderName("???").isNotBlank())
    }
}
