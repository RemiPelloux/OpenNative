package app.gamenative.provider

object ProviderGameUi {
    fun title(item: ProviderFeedItem): String = HtmlText.decode(item.title)

    fun description(item: ProviderFeedItem): String = HtmlText.plain(item.description)

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
