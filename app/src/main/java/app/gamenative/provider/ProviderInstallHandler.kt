package app.gamenative.provider

import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import java.io.File
import timber.log.Timber

object ProviderInstallHandler {
    private const val MAX_ARCHIVE_LAYERS = 4

    suspend fun install(
        transfers: ProviderTransferCoordinator,
        job: TransferJob,
        item: ProviderFeedItem,
        tab: ProviderTab,
    ): TransferJob {
        val dest = ProviderLocalPayload.folder(item)
        val payload = ProviderLocalPayload.resolve(job, item) ?: throw ProviderException(
            ProviderErrorCode.MALFORMED_RESPONSE,
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
        if (archive != null) {
            val password = transfers.archivePassword(item.description, item.link)
            val extractionRoots = mutableListOf<File>()
            try {
                val installable = extractInstallablePayload(
                    transfers = transfers,
                    job = aligned,
                    initialArchive = archive,
                    password = password,
                    extractOnly = ProviderTabPolicy.extractOnly(tab.feedUrl),
                    extractionRoots = extractionRoots,
                )
                publishExtracted(installable.absolutePath, dest, job.jobId)
            } finally {
                extractionRoots.asReversed().forEach(File::deleteRecursively)
            }
            saveCover(item, dest)
            val ready = finish(transfers, aligned, tab, dest)
            if (shouldDeleteArchive(tab) && archive.exists() && !archive.delete()) {
                Timber.tag("ProviderInstall").w("Could not delete extracted archive: ${archive.absolutePath}")
            }
            return ready
        }
        if (ProviderTabPolicy.extractOnly(tab.feedUrl)) {
            throw ProviderException(
                ProviderErrorCode.MALFORMED_RESPONSE,
                "Skidrow downloads must be a zip, rar, 7z, or ISO archive",
            )
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
        return findArchives(payload).firstOrNull()
    }

    internal fun findArchives(payload: File): List<File> {
        val files = if (payload.isFile) listOf(payload) else payload.walkTopDown().filter { it.isFile }.toList()
        return files
            .filter { file ->
                runCatching { PayloadClassifier.classify(file) }.getOrNull() == PayloadKind.PORTABLE_ARCHIVE
            }
            .sortedWith(compareBy<File>({ archivePriority(it.name) }, { it.name.lowercase() }))
    }

    private suspend fun extractInstallablePayload(
        transfers: ProviderTransferCoordinator,
        job: TransferJob,
        initialArchive: File,
        password: String,
        extractOnly: Boolean,
        extractionRoots: MutableList<File>,
    ): File {
        var archive = initialArchive
        repeat(MAX_ARCHIVE_LAYERS) { layer ->
            val extracted = transfers.extractArchive(
                job = job.copy(finalPath = archive.absolutePath),
                password = password,
                layer = layer,
            )
            val root = File(extracted.destinationPath)
            extractionRoots += root
            if (hasInstallablePayload(root)) return root

            val nested = findArchives(root)
            if (nested.isEmpty()) {
                if (!extractOnly && ProviderLocalPayload.hasPayload(root)) return root
                throw ProviderException(
                    ProviderErrorCode.MALFORMED_RESPONSE,
                    "Archive extracted successfully but contains no Windows game or installer",
                )
            }
            archive = nested.first()
        }
        throw ProviderException(
            ProviderErrorCode.MALFORMED_RESPONSE,
            "Archive nesting exceeds the supported safety limit",
        )
    }

    private fun hasInstallablePayload(root: File): Boolean =
        ExecutableDiscovery.discover(root).isNotEmpty() || ProviderLocalPayload.findInstaller(root) != null

    private fun archivePriority(name: String): Int {
        val lower = name.lowercase()
        return when {
            Regex(".*\\.part0*1\\.rar$").matches(lower) -> 0
            lower.endsWith(".rar") -> 1
            lower.endsWith(".7z.001") || lower.endsWith(".zip.001") -> 2
            lower.endsWith(".7z") || lower.endsWith(".zip") -> 3
            lower.endsWith(".iso") -> 4
            else -> 5
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

    internal fun publishExtracted(sourcePath: String, dest: File, jobId: String) {
        val source = File(sourcePath)
        if (!ProviderLocalPayload.hasPayload(source)) {
            throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Extracted archive is empty")
        }
        val parent = dest.parentFile
            ?: throw ProviderException(ProviderErrorCode.PATH_ESCAPE, "Game destination has no parent folder")
        if (!parent.mkdirs() && !parent.isDirectory) {
            throw ProviderException(ProviderErrorCode.PATH_ESCAPE, "Could not create the game library folder")
        }
        val publishing = File(parent, ".${dest.name}.installing-$jobId")
        publishing.deleteRecursively()
        try {
            if (!source.copyRecursively(publishing, overwrite = true)) {
                throw ProviderException(ProviderErrorCode.UNKNOWN, "Could not copy the extracted game")
            }
            if (dest.isDirectory && dest.listFiles().isNullOrEmpty()) dest.delete()
            if (dest.exists()) {
                throw ProviderException(
                    ProviderErrorCode.UNKNOWN,
                    "The game destination already contains files; remove it before retrying",
                )
            }
            if (!publishing.renameTo(dest)) {
                throw ProviderException(ProviderErrorCode.UNKNOWN, "Could not publish the extracted game")
            }
        } finally {
            publishing.deleteRecursively()
        }
    }
}
