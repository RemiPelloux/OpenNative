package app.gamenative.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.gamenative.provider.CleanupPolicy
import app.gamenative.provider.FeedKind
import app.gamenative.provider.ProviderTab
import app.gamenative.provider.RefreshPolicy

@Entity(tableName = "provider_tabs", indices = [Index("position")])
data class ProviderTabEntity(
    @PrimaryKey
    @ColumnInfo("tab_id")
    val tabId: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("position")
    val position: Int,
    @ColumnInfo("enabled")
    val enabled: Boolean,
    @ColumnInfo("feed_url")
    val feedUrl: String,
    @ColumnInfo("feed_kind")
    val feedKind: String,
    @ColumnInfo("credential_ref")
    val credentialRef: String?,
    @ColumnInfo("install_tree_uri")
    val installTreeUri: String,
    @ColumnInfo("cleanup_policy")
    val cleanupPolicy: String,
    @ColumnInfo("refresh_policy")
    val refreshPolicy: String,
    @ColumnInfo("etag")
    val etag: String?,
    @ColumnInfo("last_modified")
    val lastModified: String?,
    @ColumnInfo("last_refresh_at")
    val lastRefreshAt: Long,
    @ColumnInfo("last_good_at")
    val lastGoodAt: Long,
    @ColumnInfo("stale")
    val stale: Boolean,
    @ColumnInfo("accent_color")
    val accentColor: String?,
    @ColumnInfo(name = "per_page", defaultValue = "100")
    val perPage: Int = 100,
    @ColumnInfo(name = "order_by", defaultValue = "date")
    val orderBy: String = "date",
    @ColumnInfo(name = "sort_order", defaultValue = "desc")
    val sortOrder: String = "desc",
    @ColumnInfo(name = "last_fetched_page", defaultValue = "0")
    val lastFetchedPage: Int = 0,
    @ColumnInfo(name = "total_pages", defaultValue = "0")
    val totalPages: Int = 0,
) {
    fun toDomain(): ProviderTab = ProviderTab(
        id = tabId,
        name = name,
        position = position,
        enabled = enabled,
        feedUrl = feedUrl,
        feedKind = FeedKind.fromStored(feedKind),
        credentialRef = credentialRef,
        installTreeUri = installTreeUri,
        cleanupPolicy = CleanupPolicy.fromStored(cleanupPolicy),
        refreshPolicy = RefreshPolicy.fromStored(refreshPolicy),
        etag = etag,
        lastModified = lastModified,
        lastRefreshAtEpochMs = lastRefreshAt,
        lastGoodAtEpochMs = lastGoodAt,
        stale = stale,
        accentColor = accentColor,
        perPage = perPage,
        orderBy = orderBy,
        order = sortOrder,
        lastFetchedPage = lastFetchedPage,
        totalPages = totalPages,
    )

    companion object {
        fun fromDomain(tab: ProviderTab): ProviderTabEntity = ProviderTabEntity(
            tabId = tab.id,
            name = tab.name,
            position = tab.position,
            enabled = tab.enabled,
            feedUrl = tab.feedUrl,
            feedKind = tab.feedKind.name,
            credentialRef = tab.credentialRef,
            installTreeUri = tab.installTreeUri,
            cleanupPolicy = tab.cleanupPolicy.name,
            refreshPolicy = tab.refreshPolicy.name,
            etag = tab.etag,
            lastModified = tab.lastModified,
            lastRefreshAt = tab.lastRefreshAtEpochMs,
            lastGoodAt = tab.lastGoodAtEpochMs,
            stale = tab.stale,
            accentColor = tab.accentColor,
            perPage = tab.perPage,
            orderBy = tab.orderBy,
            sortOrder = tab.order,
            lastFetchedPage = tab.lastFetchedPage,
            totalPages = tab.totalPages,
        )
    }
}
