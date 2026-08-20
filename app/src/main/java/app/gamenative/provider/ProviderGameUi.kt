package app.gamenative.provider

import app.gamenative.utils.StorageUtils

object ProviderGameUi {
    fun title(item: ProviderFeedItem): String = HtmlText.decode(item.title)

    fun description(item: ProviderFeedItem): String = HtmlText.plain(item.description)

    fun resolvedSizes(item: ProviderFeedItem): Pair<Long, Long> {
        if (item.downloadSizeBytes > 0L || item.uncompressedSizeBytes > 0L) {
            return item.downloadSizeBytes to item.uncompressedSizeBytes
        }
        return WordpressMetadata.sizes(item.description)
    }

    fun sizeLine(item: ProviderFeedItem): String {
        val (download, original) = resolvedSizes(item)
        return when {
            download > 0L && original > 0L ->
                "${StorageUtils.formatBinarySize(download)} · ${StorageUtils.formatBinarySize(original)}"
            download > 0L -> StorageUtils.formatBinarySize(download)
            original > 0L -> StorageUtils.formatBinarySize(original)
            else -> ""
        }
    }

    fun canInstall(job: TransferJob?): Boolean {
        val path = job?.finalPath.orEmpty()
        return path.isNotBlank() &&
            job?.state != TransferState.READY &&
            job?.state != TransferState.DOWNLOADING
    }

    fun isBusy(job: TransferJob?): Boolean =
        job?.state == TransferState.DOWNLOADING || job?.state == TransferState.RESOLVING

    fun statusLabel(job: TransferJob): String = when (job.state) {
        TransferState.READY -> "Ready in Custom"
        TransferState.DOWNLOADING -> "Downloading ${job.progressPercent}%"
        TransferState.RESOLVING -> "Resolving link"
        TransferState.INSTALLING, TransferState.VERIFYING_INSTALL -> "Installing"
        TransferState.FAILED -> "Download failed"
        TransferState.NEEDS_REVIEW -> "Needs review"
        else -> job.state.name.lowercase().replace('_', ' ')
    }
}
