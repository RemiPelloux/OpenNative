package app.gamenative.provider

import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import app.gamenative.utils.CustomGameScanner
import java.io.File

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
        if (payload.isDirectory && ProviderLocalPayload.hasPayload(payload)) {
            return finish(transfers, aligned, tab, payload)
        }
        val kind = runCatching { PayloadClassifier.classify(payload) }.getOrDefault(PayloadKind.UNKNOWN)
        dest.mkdirs()
        if (kind == PayloadKind.PORTABLE_ARCHIVE && isZip(payload)) {
            val extracted = transfers.extractPortable(aligned)
            copyExtracted(extracted.destinationPath, dest)
            File(extracted.destinationPath).deleteRecursively()
        } else {
            payload.copyTo(File(dest, payload.name), overwrite = true)
        }
        return finish(transfers, aligned, tab, dest)
    }

    private suspend fun finish(
        transfers: ProviderTransferCoordinator,
        job: TransferJob,
        tab: ProviderTab,
        dest: File,
    ): TransferJob {
        val exe = ExecutableDiscovery.discover(dest).firstOrNull() ?: dest
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

    private fun isZip(file: File): Boolean = file.inputStream().use { stream ->
        val header = ByteArray(2)
        stream.read(header)
        header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
    }

    private fun copyExtracted(sourcePath: String, dest: File) {
        val source = File(sourcePath)
        if (!source.exists()) return
        source.copyRecursively(dest, overwrite = true)
    }
}
