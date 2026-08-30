package app.gamenative.provider

import app.gamenative.utils.StorageUtils
import java.io.File

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

    fun isInstalled(job: TransferJob?, item: ProviderFeedItem? = null, root: File? = null): Boolean =
        ProviderLocalPayload.roots(job, item, root).any { ExecutableDiscovery.discover(it).isNotEmpty() }

    fun canInstall(job: TransferJob?, item: ProviderFeedItem? = null): Boolean {
        if (isBusy(job) || isInstalled(job, item)) return false
        return ProviderLocalPayload.roots(job, item).any {
            ProviderLocalPayload.findInstaller(it) != null || ProviderLocalPayload.hasPayload(it)
        }
    }

    fun isBusy(job: TransferJob?): Boolean = job?.state in setOf(
        TransferState.QUEUED,
        TransferState.DOWNLOADING,
        TransferState.INSTALLING,
        TransferState.VERIFYING_INSTALL,
    )

    fun canRestart(job: TransferJob?): Boolean =
        job == null || job.state == TransferState.FAILED || job.state == TransferState.RESOLVING

    fun statusLabel(job: TransferJob, hasLocalInstaller: Boolean = false): String = when {
        job.state == TransferState.READY -> "Ready in Custom"
        job.state == TransferState.VERIFYING ||
            (job.state == TransferState.FAILED && hasLocalInstaller) -> "Ready to install"
        job.state == TransferState.DOWNLOADING -> "Downloading ${job.progressPercent}%"
        job.state == TransferState.RESOLVING -> "Resolving link"
        job.state == TransferState.INSTALLING || job.state == TransferState.VERIFYING_INSTALL -> "Installing"
        job.state == TransferState.FAILED && job.finalPath.isNotBlank() -> "Install failed"
        job.state == TransferState.FAILED -> "Download failed"
        job.state == TransferState.NEEDS_REVIEW -> "Needs review"
        else -> job.state.name.lowercase().replace('_', ' ')
    }

    fun visibleError(job: TransferJob?, hasLocalInstaller: Boolean = false): String {
        if (job == null || job.errorMessage.isBlank() || hasLocalInstaller) return ""
        return job.errorMessage
    }
}
