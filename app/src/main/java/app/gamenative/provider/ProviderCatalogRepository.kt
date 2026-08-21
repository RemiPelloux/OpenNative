package app.gamenative.provider

import app.gamenative.db.dao.ProviderFeedItemDao
import app.gamenative.db.dao.ProviderTabDao
import app.gamenative.db.entity.ProviderFeedItemEntity
import app.gamenative.db.entity.ProviderTabEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class ProviderCatalogRepository @Inject constructor(
    private val tabDao: ProviderTabDao,
    private val itemDao: ProviderFeedItemDao,
    private val feedClient: ProviderFeedClient,
) {
    private val refreshGuard = InFlightGuard()

    fun observeTabs(): Flow<List<ProviderTab>> = tabDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    suspend fun getTabs(): List<ProviderTab> = tabDao.getAll().map { it.toDomain() }

    suspend fun getTab(id: String): ProviderTab? = tabDao.getById(id)?.toDomain()

    suspend fun getItem(tabId: String, itemId: String): ProviderFeedItem? =
        itemDao.get(tabId, itemId)?.toDomain()

    fun observeItems(tabId: String): Flow<List<ProviderFeedItem>> =
        itemDao.observeForTab(tabId).map { rows ->
            CatalogFilter.withoutNoise(rows.map { it.toDomain() })
        }

    suspend fun createTab(draft: ProviderTab): ProviderTab {
        ProviderUrlPolicy.validate(draft.feedUrl).getOrThrow()
        val position = tabDao.maxPosition() + 1
        val tab = draft.copy(
            id = draft.id.ifBlank { UUID.randomUUID().toString() },
            position = position,
        )
        tabDao.upsert(ProviderTabEntity.fromDomain(tab))
        return tab
    }

    suspend fun updateTab(tab: ProviderTab) {
        ProviderUrlPolicy.validate(tab.feedUrl).getOrThrow()
        tabDao.upsert(ProviderTabEntity.fromDomain(tab))
    }

    suspend fun deleteTab(tabId: String) {
        itemDao.deleteForTab(tabId)
        tabDao.delete(tabId)
    }

    suspend fun refreshTab(
        tab: ProviderTab,
        pageLimit: Int = ProviderUrlPolicy.STARTUP_PAGE_LIMIT,
        search: String = "",
    ): ProviderTab {
        if (!ProviderSessionGate.allowCatalogWork()) return tab
        val result = refreshGuard.withKey(tab.id) {
            fetchPages(tab, pageLimit, search = search)
        }
        return result ?: tab
    }

    suspend fun searchLocal(tabId: String, query: String): List<ProviderFeedItem> {
        val like = "%${query.trim()}%"
        return itemDao.search(tabId, like).map { it.toDomain() }
    }

    suspend fun loadMore(tab: ProviderTab, search: String = ""): ProviderTab {
        if (!ProviderSessionGate.allowCatalogWork()) return tab
        if (!ProviderCatalogPaging.canLoadMore(tab)) return tab
        val nextPage = tab.lastFetchedPage + 1
        return refreshGuard.withKey("${tab.id}:more") {
            runCatching {
                fetchPages(tab, pageLimit = 1, startPage = nextPage, replace = false, search = search)
            }.getOrElse { error ->
                Timber.tag("ProviderCatalog").w(error, "Load more failed")
                tab
            }
        } ?: tab
    }

    suspend fun searchPage(tab: ProviderTab, query: String, page: Int): ProviderSearchPage =
        withContext(Dispatchers.IO) {
        val needle = query.trim()
        if (needle.isBlank()) return@withContext ProviderSearchPage(emptyList(), false)
        val fetchUrl = ProviderFeedTarget.resolve(tab.feedUrl)
        val style = FeedPaginator.detectStyle(fetchUrl, tab.feedKind)
        val pageResult = feedClient.fetch(
            url = fetchUrl,
            kindHint = ProviderFeedTarget.kindHint(tab.feedUrl, tab.feedKind),
            page = page,
            perPage = tab.perPage,
            orderBy = tab.orderBy,
            order = tab.order,
            search = needle,
        )
        val items = CatalogFilter.withoutNoise(pageResult.items)
            .map { WordpressMetadata.restrictForFeed(it, tab.feedUrl) }
        val more = FeedPaginator.hasMore(
            fetchedPage = page,
            itemCount = pageResult.items.size,
            perPage = tab.perPage,
            totalPages = pageResult.totalPages,
            nextCursor = pageResult.nextCursor,
            style = style,
        )
        ProviderSearchPage(items, more)
        }

    private suspend fun fetchPages(
        tab: ProviderTab,
        pageLimit: Int,
        startPage: Int = 1,
        replace: Boolean = true,
        search: String = "",
    ): ProviderTab = withContext(Dispatchers.IO) {
        var cursor: String? = null
        var latest = tab
        val fetchUrl = ProviderFeedTarget.resolve(latest.feedUrl)
        val style = FeedPaginator.detectStyle(fetchUrl, latest.feedKind)
        val collected = ArrayList<ProviderFeedItemEntity>(pageLimit * latest.perPage)
        var lastItemCount = 0
        var lastTotalPages: Int? = null
        repeat(pageLimit) { offset ->
            val pageNumber = startPage + offset
            val conditional = style != PaginationStyle.SINGLE_DOCUMENT &&
                style != PaginationStyle.SKIDROW_RSS &&
                offset == 0 && replace && search.isBlank()
            val page = feedClient.fetch(
                url = fetchUrl,
                cursor = cursor,
                etag = if (conditional) latest.etag else null,
                lastModified = if (conditional) latest.lastModified else null,
                kindHint = ProviderFeedTarget.kindHint(latest.feedUrl, latest.feedKind),
                page = pageNumber,
                perPage = latest.perPage,
                orderBy = latest.orderBy,
                order = latest.order,
                search = search,
            )
            if (page.notModified) {
                return@withContext persist(latest.copy(stale = false, lastRefreshAtEpochMs = System.currentTimeMillis()))
            }
            collected += CatalogFilter.withoutNoise(page.items)
                .map { WordpressMetadata.restrictForFeed(it, latest.feedUrl) }
                .map { ProviderFeedItemEntity.fromDomain(latest.id, it) }
            lastItemCount = page.items.size
            lastTotalPages = page.totalPages
            cursor = page.nextCursor
            val more = FeedPaginator.hasMore(
                fetchedPage = pageNumber,
                itemCount = lastItemCount,
                perPage = latest.perPage,
                totalPages = lastTotalPages,
                nextCursor = cursor,
                style = style,
            )
            latest = latest.copy(
                etag = page.etag ?: latest.etag,
                lastModified = page.lastModified ?: latest.lastModified,
                lastRefreshAtEpochMs = System.currentTimeMillis(),
                lastGoodAtEpochMs = System.currentTimeMillis(),
                stale = false,
                lastFetchedPage = pageNumber,
                totalPages = lastTotalPages ?: if (!more) pageNumber else latest.totalPages,
            )
            if (!more) return@withContext commitPages(latest, collected, replace)
        }
        commitPages(latest, collected, replace)
    }

    private suspend fun commitPages(
        tab: ProviderTab,
        items: List<ProviderFeedItemEntity>,
        replace: Boolean,
    ): ProviderTab {
        itemDao.replacePage(tab.id, items, replaceAll = replace)
        return persist(tab)
    }

    private suspend fun persist(tab: ProviderTab): ProviderTab {
        tabDao.upsert(ProviderTabEntity.fromDomain(tab))
        return tab
    }
}
