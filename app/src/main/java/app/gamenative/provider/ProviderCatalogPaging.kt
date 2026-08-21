package app.gamenative.provider

object ProviderCatalogPaging {
    fun canLoadMore(tab: ProviderTab): Boolean {
        val style = styleOf(tab)
        return FeedPaginator.hasMore(
            fetchedPage = tab.lastFetchedPage,
            itemCount = tab.perPage,
            perPage = tab.perPage,
            totalPages = tab.totalPages.takeIf { it > 0 },
            nextCursor = null,
            style = style,
        )
    }

    fun needsCatalogRepair(tab: ProviderTab): Boolean {
        if (styleOf(tab) != PaginationStyle.SKIDROW_RSS) return false
        return tab.totalPages > 0 && tab.lastFetchedPage >= tab.totalPages
    }

    private fun styleOf(tab: ProviderTab): PaginationStyle =
        FeedPaginator.detectStyle(ProviderFeedTarget.resolve(tab.feedUrl), tab.feedKind)
}

data class ProviderSearchPage(
    val items: List<ProviderFeedItem>,
    val hasMore: Boolean,
)
