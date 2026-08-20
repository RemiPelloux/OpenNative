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
    fun `preferred link falls back to the post url`() {
        val item = ProviderFeedItem(
            itemId = "1",
            title = "Game",
            link = "https://example.com/post",
            extraJson = "{}",
        )
        assertEquals("https://example.com/post", WordpressMetadata.preferredLink(item))
    }

    @Test
    fun `folder name stays filesystem safe`() {
        assertEquals("Twisted Tower  v1.0.3", ProviderInstallHandler.folderName("Twisted Tower – v1.0.3"))
        assertTrue(ProviderInstallHandler.folderName("???").isNotBlank())
    }
}
