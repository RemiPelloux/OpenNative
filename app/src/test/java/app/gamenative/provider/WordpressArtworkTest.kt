package app.gamenative.provider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordpressArtworkTest {
    @Test
    fun `prefers jetpack featured media`() {
        val url = WordpressArtwork.from(
            JSONObject(
                """
                {
                  "jetpack_featured_media_url": "https://cdn.example.com/jetpack.png",
                  "content": { "rendered": "<img src=\"https://cdn.example.com/other.jpg\" />" }
                }
                """.trimIndent(),
            ),
        )
        assertEquals("https://cdn.example.com/jetpack.png", url)
    }

    @Test
    fun `reads first https image and ignores magnets`() {
        val url = WordpressArtwork.from(
            JSONObject(
                """
                {
                  "content": {
                    "rendered": "<p><a href=\"magnet:?xt=urn:btih:abc\">x</a><img src=\"https://i8.imageban.ru/out/2026/08/19/cover.jpg\" /></p>"
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals("https://i8.imageban.ru/out/2026/08/19/cover.jpg", url)
    }

    @Test
    fun `ignores non https artwork`() {
        assertNull(
            WordpressArtwork.from(
                JSONObject("""{"jetpack_featured_media_url":"http://cdn.example.com/cover.jpg"}"""),
            ),
        )
    }
}
