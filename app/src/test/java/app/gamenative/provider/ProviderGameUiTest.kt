package app.gamenative.provider

import org.junit.Assert.assertEquals
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
}
