package app.gamenative.provider

data class ProviderTab(
    val id: String,
    val name: String,
    val position: Int,
    val enabled: Boolean = true,
    val feedUrl: String,
    val feedKind: FeedKind = FeedKind.JSON,
    val credentialRef: String? = null,
    val installTreeUri: String = "",
    val cleanupPolicy: CleanupPolicy = CleanupPolicy.DELETE_AFTER_VERIFIED_INSTALL,
    val refreshPolicy: RefreshPolicy = RefreshPolicy.DAILY,
    val etag: String? = null,
    val lastModified: String? = null,
    val lastRefreshAtEpochMs: Long = 0L,
    val lastGoodAtEpochMs: Long = 0L,
    val stale: Boolean = false,
    val accentColor: String? = null,
    val perPage: Int = ProviderUrlPolicy.PAGE_SIZE,
    val orderBy: String = "date",
    val order: String = "desc",
    val lastFetchedPage: Int = 0,
    val totalPages: Int = 0,
) {
    fun hasCredential(): Boolean = !credentialRef.isNullOrBlank()
}
