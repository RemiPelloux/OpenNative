package app.gamenative.provider

data class InstallReceipt(
    val receiptId: String,
    val jobId: String,
    val tabId: String,
    val itemId: String,
    val installerHash: String,
    val containerId: String = "",
    val destinationPath: String,
    val executablePath: String,
    val cleanupResult: String,
    val createdAtEpochMs: Long,
)
