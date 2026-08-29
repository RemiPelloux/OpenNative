package app.gamenative.provider

import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import java.io.File
import timber.log.Timber

object ProviderInstallHandler {
    suspend fun install(
        transfers: ProviderTransferCoordinator,
        job: TransferJob,
        item: ProviderFeedItem,
        tab: ProviderTab,
    ): TransferJob {
        val dest = ProviderLocalPayload.folder(item)
        val payload = ProviderLocalPayload.resolve(job, item) ?: return transfers.markFailed(
            job,
            "Downloaded file is missing",
        )
        val aligned = job.copy(
            finalPath = payload.absolutePath,
            destinationPath = if (payload.isDirectory) payload.absolutePath else dest.absolutePath,
            errorMessage = "",
            errorCode = null,
        )
        if (payload.isDirectory && ExecutableDiscovery.discover(payload).isNotEmpty()) {
            saveCover(item, payload)
            return finish(transfers, aligned, tab, payload)
        }
        val archive = findArchive(payload)
        dest.mkdirs()
        if (archive != null) {
            val extracted = transfers.extractArchive(aligned.copy(finalPath = archive.absolutePath))
            copyExtracted(extracted.destinationPath, dest)
            File(extracted.destinationPath).deleteRecursively()
            saveCover(item, dest)
            val ready = finish(transfers, aligned, tab, dest)
            if (shouldDeleteArchive(tab) && archive.exists() && !archive.delete()) {
                Timber.tag("ProviderInstall").w("Could not delete extracted archive: ${archive.absolutePath}")
            }
            return ready
        }
        if (ProviderTabPolicy.extractOnly(tab.feedUrl)) {
            return transfers.markFailed(aligned, "Skidrow downloads must be a zip, rar, 7z, or ISO archive")
        }
        if (payload.isDirectory && ProviderLocalPayload.hasPayload(payload)) {
            saveCover(item, payload)
            return finish(transfers, aligned, tab, payload)
        }
        payload.copyTo(File(dest, payload.name), overwrite = true)
        saveCover(item, dest)
        return finish(transfers, aligned, tab, dest)
    }

    internal fun shouldDeleteArchive(tab: ProviderTab): Boolean =
        ProviderTabPolicy.extractOnly(tab.feedUrl) || tab.cleanupPolicy != CleanupPolicy.KEEP

    internal fun shouldLaunchSetup(tab: ProviderTab, destination: File): Boolean {
        if (ProviderLocalPayload.findInstaller(destination) == null) return false
        return !ProviderTabPolicy.extractOnly(tab.feedUrl) || ExecutableDiscovery.discover(destination).isEmpty()
    }

    internal fun findArchive(payload: File): File? {
        val files = if (payload.isFile) listOf(payload) else payload.walkTopDown().filter { it.isFile }.toList()
        return files.firstOrNull { file ->
            runCatching { PayloadClassifier.classify(file) }.getOrNull() == PayloadKind.PORTABLE_ARCHIVE
        }
    }

    private suspend fun finish(
        transfers: ProviderTransferCoordinator,
        job: TransferJob,
        tab: ProviderTab,
        dest: File,
    ): TransferJob {
        val exe = ExecutableDiscovery.pickLaunchExe(dest, job.title) ?: dest
        val ready = transfers.completePortableInstall(
            job.copy(destinationPath = dest.absolutePath),
            exe,
            tab.cleanupPolicy,
            confirmed = false,
        )
        PluviaApp.events.emit(AndroidEvent.CustomGameFolderAdded(dest.absolutePath))
        return ready
    }

    fun folderName(title: String): String = ProviderPathSlug.slug(title)

    private fun saveCover(item: ProviderFeedItem, dest: File) {
        runCatching { ProviderCoverStore.save(item.artworkUrl, dest) }
    }

    private fun copyExtracted(sourcePath: String, dest: File) {
        val source = File(sourcePath)
        if (!source.exists()) return
        source.copyRecursively(dest, overwrite = true)
    }
}
