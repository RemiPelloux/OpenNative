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
        val payload = File(job.finalPath)
        if (!payload.exists()) {
            return transfers.markFailed(job, "Downloaded file is missing")
        }
        val dest = File(CustomGameScanner.defaultRootPath, folderName(item.title))
        dest.mkdirs()
        val kind = runCatching { PayloadClassifier.classify(payload) }.getOrDefault(PayloadKind.UNKNOWN)
        if (kind == PayloadKind.PORTABLE_ARCHIVE && isZip(payload)) {
            val extracted = transfers.extractPortable(job)
            copyExtracted(extracted.destinationPath, dest)
            File(extracted.destinationPath).deleteRecursively()
        } else {
            payload.copyTo(File(dest, payload.name), overwrite = true)
        }
        val exe = ExecutableDiscovery.discover(dest).firstOrNull() ?: dest
        val ready = transfers.completePortableInstall(
            job.copy(destinationPath = dest.absolutePath),
            exe,
            tab.cleanupPolicy,
            confirmed = true,
        )
        PluviaApp.events.emit(AndroidEvent.CustomGameFolderAdded(dest.absolutePath))
        return ready
    }

    fun folderName(title: String): String =
        title.replace(Regex("[^A-Za-z0-9._ -]"), "").trim().ifBlank { "game" }.take(72)

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
