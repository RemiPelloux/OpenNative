package app.gamenative.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import app.gamenative.provider.HtmlText
import app.gamenative.provider.ProviderFeedItem

@Entity(
    tableName = "provider_feed_items",
    primaryKeys = ["tab_id", "item_id"],
    indices = [Index("tab_id"), Index("title")],
)
data class ProviderFeedItemEntity(
    @ColumnInfo("tab_id")
    val tabId: String,
    @ColumnInfo("item_id")
    val itemId: String,
    @ColumnInfo("title")
    val title: String,
    @ColumnInfo("version")
    val version: String,
    @ColumnInfo("architecture")
    val architecture: String,
    @ColumnInfo("download_size")
    val downloadSize: Long,
    @ColumnInfo("uncompressed_size")
    val uncompressedSize: Long,
    @ColumnInfo("sha256")
    val sha256: String?,
    @ColumnInfo("artwork_url")
    val artworkUrl: String?,
    @ColumnInfo("description")
    val description: String,
    @ColumnInfo("link")
    val link: String,
    @ColumnInfo("profile_ref")
    val profileRef: String?,
    @ColumnInfo("published_at")
    val publishedAt: Long,
    @ColumnInfo("extra_json")
    val extraJson: String,
) {
    fun toDomain(): ProviderFeedItem = ProviderFeedItem(
        itemId = itemId,
        title = HtmlText.decode(title),
        version = version,
        architecture = architecture,
        downloadSizeBytes = downloadSize,
        uncompressedSizeBytes = uncompressedSize,
        sha256 = sha256,
        artworkUrl = artworkUrl,
        description = HtmlText.plain(description),
        link = link,
        profileRef = profileRef,
        publishedAtEpochMs = publishedAt,
        extraJson = extraJson,
    )

    companion object {
        fun fromDomain(tabId: String, item: ProviderFeedItem): ProviderFeedItemEntity =
            ProviderFeedItemEntity(
                tabId = tabId,
                itemId = item.itemId,
                title = item.title,
                version = item.version,
                architecture = item.architecture,
                downloadSize = item.downloadSizeBytes,
                uncompressedSize = item.uncompressedSizeBytes,
                sha256 = item.sha256,
                artworkUrl = item.artworkUrl,
                description = item.description,
                link = item.link,
                profileRef = item.profileRef,
                publishedAt = item.publishedAtEpochMs,
                extraJson = item.extraJson,
            )
    }
}
