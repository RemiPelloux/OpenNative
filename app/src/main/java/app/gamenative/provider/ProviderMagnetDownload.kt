package app.gamenative.provider

import java.io.File
import timber.log.Timber

object ProviderMagnetDownload {
    suspend fun downloadAll(
        apiKey: String,
        magnet: String,
        job: TransferJob,
        title: String,
        resolver: MagnetDebridResolver,
        downloader: StreamingDownloader,
        cancelled: () -> Boolean,
        persist: suspend (TransferJob) -> TransferJob,
        onProgress: (done: Long, total: Long) -> Unit,
    ): TransferJob {
        persist(job.copy(state = TransferState.RESOLVING, selectedLink = magnet))
        val uploaded = resolver.uploadMagnet(apiKey, magnet)
        Timber.tag("ProviderTransfer").i("Uploaded magnet id=${uploaded.id} ready=${uploaded.ready}")
        if (!uploaded.ready) resolver.waitMagnetReady(apiKey, uploaded.id)
        val files = resolver.magnetFiles(apiKey, uploaded.id)
        if (files.isEmpty()) {
            throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Magnet has no downloadable files")
        }
        val dest = ProviderPathSlug.ensureFolder(title, ProviderLocalPayload.writeRoot())
        var downloaded = persist(
            job.copy(
                state = TransferState.DOWNLOADING,
                selectedLink = magnet,
                destinationPath = dest.absolutePath,
                bytesTotal = files.sumOf { it.sizeBytes },
            ),
        )
        var completed = 0L
        files.forEach { file ->
            downloaded = writeFile(apiKey, file, dest, downloaded, completed, resolver, downloader, cancelled, persist, onProgress)
            completed += file.sizeBytes.coerceAtLeast(0L)
        }
        return persist(
            downloaded.copy(
                state = TransferState.VERIFYING,
                finalPath = dest.absolutePath,
                destinationPath = dest.absolutePath,
                progressPercent = 100,
            ),
        )
    }

    private suspend fun writeFile(
        apiKey: String,
        file: MagnetRemoteFile,
        dest: File,
        job: TransferJob,
        completed: Long,
        resolver: MagnetDebridResolver,
        downloader: StreamingDownloader,
        cancelled: () -> Boolean,
        persist: suspend (TransferJob) -> TransferJob,
        onProgress: (done: Long, total: Long) -> Unit,
    ): TransferJob {
        val target = confinedTarget(dest, file)
        if (isComplete(target, file.sizeBytes)) {
            File(target.parentFile, "${target.name}.partial").delete()
            return persist(job.copy(filename = file.relativePath, bytesDownloaded = completed + target.length()))
        }
        val unlocked = resolver.resolve(apiKey, file.link)
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "${target.name}.partial")
        val expected = unlocked.sizeBytes.takeIf { it > 0L } ?: file.sizeBytes
        downloader.download(
            url = unlocked.url,
            partialFile = partial,
            expectedBytes = expected,
            onProgress = { done, _ -> onProgress(completed + done, job.bytesTotal) },
            isCancelled = cancelled,
        )
        val finalFile = downloader.promote(partial)
        if (finalFile.absolutePath != target.absolutePath) {
            if (target.exists()) target.delete()
            finalFile.renameTo(target)
        }
        return persist(job.copy(filename = file.relativePath, bytesDownloaded = completed + expected))
    }

    internal fun confinedTarget(dest: File, file: MagnetRemoteFile): File {
        val flat = ArchiveInspector.confinedPath(dest, ProviderPathSlug.fileName(file.relativePath))
        if (isComplete(flat, file.sizeBytes)) return flat
        val nested = ArchiveInspector.confinedPath(dest, ProviderPathSlug.slugDirectories(file.relativePath))
        if (nested != flat && isComplete(nested, file.sizeBytes)) {
            if (nested.renameTo(flat)) return flat
            return nested
        }
        return flat
    }

    private fun isComplete(file: File, sizeBytes: Long): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        return sizeBytes <= 0L || file.length() >= sizeBytes
    }
}
