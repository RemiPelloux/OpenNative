package app.gamenative.provider

data class ProviderFeedItem(
    val itemId: String,
    val title: String,
    val version: String = "",
    val architecture: String = "",
    val downloadSizeBytes: Long = 0L,
    val uncompressedSizeBytes: Long = 0L,
    val sha256: String? = null,
    val artworkUrl: String? = null,
    val description: String = "",
    val link: String,
    val profileRef: String? = null,
    val publishedAtEpochMs: Long = 0L,
    val extraJson: String = "{}",
)
