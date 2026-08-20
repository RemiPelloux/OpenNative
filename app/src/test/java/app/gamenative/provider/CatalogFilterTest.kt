package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogFilterTest {
    private val items = listOf(
        ProviderFeedItem(itemId = "1", title = "Portal", link = "https://example.com/a"),
        ProviderFeedItem(itemId = "2", title = "Other", description = "A puzzle game", link = "https://example.com/b"),
        ProviderFeedItem(itemId = "3", title = "Racing", link = "https://example.com/c"),
    )

    @Test
    fun `filters by title case insensitively`() {
        assertEquals(listOf("1"), CatalogFilter.filter(items, "PORTAL").map { it.itemId })
    }

    @Test
    fun `filters by description`() {
        assertEquals(listOf("2"), CatalogFilter.filter(items, "puzzle").map { it.itemId })
    }

    @Test
    fun `blank query returns every item`() {
        assertEquals(3, CatalogFilter.filter(items, "  ").size)
    }
}
