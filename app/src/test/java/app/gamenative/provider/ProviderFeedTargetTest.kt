package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFeedTargetTest {
    @Test
    fun `skidrow feedburner resolves to the site rss host`() {
        val feed = "https://feeds.feedburner.com/SkidrowReloadedGames"
        assertEquals("https://www.skidrowreloaded.com/", ProviderFeedTarget.resolve(feed))
        assertEquals(FeedKind.RSS, ProviderFeedTarget.kindHint(feed, FeedKind.JSON))
        assertEquals(
            PaginationStyle.SKIDROW_RSS,
            FeedPaginator.detectStyle(ProviderFeedTarget.resolve(feed), FeedKind.RSS),
        )
    }

    @Test
    fun `fitgirl stays on its wordpress rest url`() {
        val feed = "https://fitgirl-repacks.site/wp-json/wp/v2/posts"
        assertEquals(feed, ProviderFeedTarget.resolve(feed))
        assertEquals(FeedKind.JSON, ProviderFeedTarget.kindHint(feed, FeedKind.JSON))
    }

    @Test
    fun `skidrow can load more after the first rss page`() {
        val tab = ProviderTab(
            id = "skidrow",
            name = "Skidrow",
            position = 1,
            feedUrl = "https://feeds.feedburner.com/SkidrowReloadedGames",
            feedKind = FeedKind.RSS,
            lastFetchedPage = 1,
            totalPages = 0,
            perPage = 20,
        )
        assertTrue(ProviderCatalogPaging.canLoadMore(tab))
        assertFalse(ProviderCatalogPaging.canLoadMore(tab.copy(lastFetchedPage = 4, totalPages = 4)))
        assertFalse(
            ProviderCatalogPaging.canLoadMore(
                tab.copy(
                    feedUrl = "https://feeds.feedburner.com/OtherGames",
                    lastFetchedPage = 1,
                    totalPages = 0,
                ),
            ),
        )
    }
}
