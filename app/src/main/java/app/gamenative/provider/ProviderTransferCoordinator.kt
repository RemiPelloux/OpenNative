package app.gamenative.provider

import app.gamenative.PrefManager
import app.gamenative.db.dao.ProviderInstallReceiptDao
import app.gamenative.db.dao.ProviderTransferJobDao
import app.gamenative.db.entity.ProviderInstallReceiptEntity
import app.gamenative.db.entity.ProviderTransferJobEntity
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Singleton
class ProviderTransferCoordinator @Inject constructor(
    private val jobDao: ProviderTransferJobDao,
    private val receiptDao: ProviderInstallReceiptDao,
    private val secrets: ProviderSecretStore,
    private val resolver: AllDebridResolver,
    private val downloader: StreamingDownloader,
    @Named("providerStagingRoot") private val stagingRoot: File,
) {
    private val resolveGuard = InFlightGuard()
    private val liveProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val lastProgressAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun observeJobs(tabId: String): Flow<List<TransferJob>> =
        combine(jobDao.observeForTab(tabId), liveProgress) { rows, progress ->
            rows.map { entity ->
                val job = entity.toDomain()
                job.copy(progressPercent = progress[job.jobId] ?: job.progressPercent)
            }
        }

    fun observeAllJobs(): Flow<List<TransferJob>> = jobDao.observeAll().mapRows()

    suspend fun enqueue(
        tab: ProviderTab,
        item: ProviderFeedItem,
        availableBytes: Long,
        includeWineHeadroom: Boolean,
    ): TransferJob {
        val required = StorageReservation.requiredBytes(
            downloadSize = item.downloadSizeBytes,
            uncompressedSize = item.uncompressedSizeBytes,
            includeWineHeadroom = includeWineHeadroom,
        )
        if (!StorageReservation.hasSpace(availableBytes, required)) {
            throw ProviderException(ProviderErrorCode.LOW_SPACE, StorageReservation.formatShortage(availableBytes, required))
        }
        val now = System.currentTimeMillis()
        val job = TransferJob(
            jobId = UUID.randomUUID().toString(),
            tabId = tab.id,
            itemId = item.itemId,
            title = item.title,
            state = TransferState.QUEUED,
            selectedLink = item.link,
            expectedSha256 = item.sha256,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        persist(job)
        return job
    }

    suspend fun resolveAndDownload(tab: ProviderTab, job: TransferJob, cancelled: () -> Boolean): TransferJob {
        val key = job.identity
        val running = resolveGuard.withKey(key) {
            val resolved = resolve(tab, job)
            download(resolved, cancelled)
        }
        return running ?: job
    }

    suspend fun completePortableInstall(job: TransferJob, selectedExe: File, policy: CleanupPolicy): TransferJob {
        val installer = File(job.finalPath)
        val hash = StreamingHasher.sha256(installer)
        if (!StreamingHasher.matches(job.expectedSha256, hash)) {
            throw ProviderException(ProviderErrorCode.HASH_MISMATCH, "Installer hash did not match")
        }
        val destination = File(job.destinationPath)
        val receipt = InstallReceipt(
            receiptId = UUID.randomUUID().toString(),
            jobId = job.jobId,
            tabId = job.tabId,
            itemId = job.itemId,
            installerHash = hash,
            destinationPath = destination.absolutePath,
            executablePath = selectedExe.absolutePath,
            cleanupResult = "pending",
            createdAtEpochMs = System.currentTimeMillis(),
        )
        receiptDao.upsert(ProviderInstallReceiptEntity.fromDomain(receipt))
        val folders = PrefManager.customGameManualFolders.toMutableSet()
        folders.add(destination.absolutePath)
        PrefManager.customGameManualFolders = folders
        val decision = CleanupGuard.evaluate(
            policy = policy,
            sessionComplete = true,
            requiredFilesPresent = destination.exists(),
            hashesMatch = true,
            executableSelected = selectedExe.exists(),
            receiptCommitted = true,
            installerOwnedByJob = installer.exists() && installer.absolutePath == job.finalPath,
        )
        if (decision.canDelete) installer.delete()
        return persist(
            job.copy(
                state = TransferState.READY,
                executablePath = selectedExe.absolutePath,
                actualSha256 = hash,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun extractPortable(job: TransferJob): TransferJob = withContext(Dispatchers.IO) {
        val archive = File(job.finalPath)
        ArchiveInspector.inspectZip(archive)
        val dest = File(stagingRoot, job.jobId)
        dest.mkdirs()
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val target = ArchiveInspector.confinedPath(dest, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                }
            }
        }
        persist(job.copy(state = TransferState.INSTALLING, destinationPath = dest.absolutePath))
    }

    private suspend fun resolve(tab: ProviderTab, job: TransferJob): TransferJob {
        val apiKey = secrets.read(tab.credentialRef)
            ?: throw ProviderException(ProviderErrorCode.AUTHENTICATION, "Resolver credential is missing")
        persist(job.copy(state = TransferState.RESOLVING))
        val resolved = resolver.resolve(apiKey, job.selectedLink)
        val partial = File(stagingRoot, "${job.jobId}/${resolved.filename}.partial")
        return persist(
            job.copy(
                state = TransferState.DOWNLOADING,
                resolvedUrl = resolved.url,
                filename = resolved.filename,
                partialPath = partial.absolutePath,
                bytesTotal = resolved.sizeBytes,
            ),
        )
    }

    private suspend fun download(job: TransferJob, cancelled: () -> Boolean): TransferJob {
        val partial = File(job.partialPath)
        val downloaded = downloader.download(
            url = job.resolvedUrl,
            partialFile = partial,
            expectedBytes = job.bytesTotal,
            onProgress = { done, total -> publishProgress(job.jobId, done, total) },
            isCancelled = cancelled,
        )
        val finalFile = downloader.promote(downloaded)
        val hash = StreamingHasher.sha256(finalFile)
        if (!StreamingHasher.matches(job.expectedSha256, hash)) {
            throw ProviderException(ProviderErrorCode.HASH_MISMATCH, "Download hash did not match")
        }
        return persist(
            job.copy(
                state = TransferState.VERIFYING,
                finalPath = finalFile.absolutePath,
                actualSha256 = hash,
                progressPercent = 100,
            ),
        )
    }

    private fun publishProgress(jobId: String, done: Long, total: Long) {
        val percent = if (total > 0L) ((done * 100L) / total).toInt() else 0
        val now = System.currentTimeMillis()
        val last = lastProgressAt[jobId] ?: 0L
        if (percent < 100 && now - last < 200L) return
        lastProgressAt[jobId] = now
        liveProgress.update { it + (jobId to percent) }
    }

    private suspend fun persist(job: TransferJob): TransferJob {
        val updated = job.copy(updatedAtEpochMs = System.currentTimeMillis())
        jobDao.upsert(ProviderTransferJobEntity.fromDomain(updated))
        return updated
    }

    private fun Flow<List<ProviderTransferJobEntity>>.mapRows(): Flow<List<TransferJob>> =
        map { rows -> rows.map { it.toDomain() } }
}
