package app.gamenative.provider

data class ProviderFeedPage(
    val items: List<ProviderFeedItem>,
    val nextCursor: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val notModified: Boolean = false,
    val page: Int = 1,
    val totalPages: Int? = null,
)
