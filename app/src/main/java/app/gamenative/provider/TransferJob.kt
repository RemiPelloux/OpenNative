package app.gamenative.provider

data class TransferJob(
    val jobId: String,
    val tabId: String,
    val itemId: String,
    val title: String,
    val state: TransferState = TransferState.IDLE,
    val selectedLink: String,
    val resolvedUrl: String = "",
    val filename: String = "",
    val partialPath: String = "",
    val finalPath: String = "",
    val destinationPath: String = "",
    val executablePath: String = "",
    val bytesDownloaded: Long = 0L,
    val bytesTotal: Long = 0L,
    val expectedSha256: String? = null,
    val actualSha256: String? = null,
    val errorCode: ProviderErrorCode? = null,
    val errorMessage: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val progressPercent: Int = 0,
) {
    val identity: String get() = "$tabId:$itemId:$selectedLink"
}
