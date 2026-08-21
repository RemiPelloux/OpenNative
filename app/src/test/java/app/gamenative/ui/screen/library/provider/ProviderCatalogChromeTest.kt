package app.gamenative.ui.screen.library.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogChromeTest {
    @Test
    fun `hides search when scrolling down the catalog`() {
        assertFalse(
            ProviderCatalogChrome.nextSearchVisible(
                visible = true,
                firstIndex = 2,
                firstOffset = 40,
                previousIndex = 1,
                previousOffset = 10,
                keepVisible = false,
            ),
        )
    }

    @Test
    fun `shows search again when scrolling up or returning to the top`() {
        assertTrue(
            ProviderCatalogChrome.nextSearchVisible(
                visible = false,
                firstIndex = 0,
                firstOffset = 4,
                previousIndex = 1,
                previousOffset = 80,
                keepVisible = false,
            ),
        )
        assertTrue(
            ProviderCatalogChrome.nextSearchVisible(
                visible = false,
                firstIndex = 1,
                firstOffset = 4,
                previousIndex = 1,
                previousOffset = 40,
                keepVisible = false,
            ),
        )
    }

    @Test
    fun `keeps search visible while a query is active`() {
        assertTrue(
            ProviderCatalogChrome.nextSearchVisible(
                visible = true,
                firstIndex = 8,
                firstOffset = 120,
                previousIndex = 2,
                previousOffset = 0,
                keepVisible = true,
            ),
        )
    }
}
