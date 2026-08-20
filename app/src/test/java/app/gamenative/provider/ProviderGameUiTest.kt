package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderGameUiTest {
    @Test
    fun `decodes cuphead title for the game sheet`() {
        val item = ProviderFeedItem(
            itemId = "5361",
            title = "Cuphead: Game &#038; Soundtrack Bundle &#8211; v1.3.9",
            link = "https://fitgirl-repacks.site/cuphead/",
        )
        assertEquals("Cuphead: Game & Soundtrack Bundle – v1.3.9", ProviderGameUi.title(item))
    }

    @Test
    fun `hides zero byte download labels`() {
        val empty = ProviderFeedItem(itemId = "1", title = "Game", link = "https://example.com/g")
        assertEquals("", ProviderGameUi.sizeLine(empty))
        val fromExcerpt = empty.copy(description = "Original Size: 6.9 GB Repack Size: 3.6/4 GB")
        assertTrue(ProviderGameUi.sizeLine(fromExcerpt).isNotBlank())
    }
}
