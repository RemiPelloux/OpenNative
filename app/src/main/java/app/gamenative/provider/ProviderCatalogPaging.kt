package app.gamenative.provider

object ProviderCatalogPaging {
    fun canLoadMore(tab: ProviderTab): Boolean {
        val resolved = ProviderFeedTarget.resolve(tab.feedUrl)
        val style = FeedPaginator.detectStyle(resolved, tab.feedKind)
        return FeedPaginator.hasMore(
            fetchedPage = tab.lastFetchedPage,
            itemCount = tab.perPage,
            perPage = tab.perPage,
            totalPages = tab.totalPages.takeIf { it > 0 },
            nextCursor = null,
            style = style,
        )
    }
}

data class ProviderSearchPage(
    val items: List<ProviderFeedItem>,
    val hasMore: Boolean,
)
