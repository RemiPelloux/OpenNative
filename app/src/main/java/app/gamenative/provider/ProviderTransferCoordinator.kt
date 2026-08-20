package app.gamenative.provider

import app.gamenative.PrefManager
import app.gamenative.db.dao.ProviderInstallReceiptDao
import app.gamenative.db.dao.ProviderTransferJobDao
import app.gamenative.db.entity.ProviderInstallReceiptEntity
import app.gamenative.db.entity.ProviderTransferJobEntity
import java.io.File
import java.util.UUID
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
import timber.log.Timber

@Singleton
class ProviderTransferCoordinator @Inject constructor(
    private val jobDao: ProviderTransferJobDao,
    private val receiptDao: ProviderInstallReceiptDao,
    private val secrets: ProviderSecretStore,
    private val resolver: AllDebridResolver,
    private val downloader: StreamingDownloader,
    private val feedClient: ProviderFeedClient,
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

    suspend fun attachExisting(tab: ProviderTab, item: ProviderFeedItem, dest: File): TransferJob {
        val now = System.currentTimeMillis()
        return persist(
            TransferJob(
                jobId = UUID.randomUUID().toString(),
                tabId = tab.id,
                itemId = item.itemId,
                title = item.title,
                state = TransferState.VERIFYING,
                selectedLink = item.link,
                finalPath = dest.absolutePath,
                destinationPath = dest.absolutePath,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
    }

    suspend fun resolveAndDownload(
        tab: ProviderTab,
        job: TransferJob,
        cancelled: () -> Boolean,
        candidates: List<String> = emptyList(),
        pageUrl: String = "",
        magnet: String = "",
    ): TransferJob {
        return withContext(Dispatchers.IO) {
            val key = job.identity
            val running = resolveGuard.withKey(key) {
                val magnetUri = magnet.ifBlank { scrapeMagnet(pageUrl) }
                if (magnetUri.isNotBlank()) {
                    downloadMagnet(tab, job, magnetUri, cancelled)
                } else {
                    val resolved = resolve(tab, job, candidates, pageUrl)
                    download(resolved, cancelled)
                }
            }
            running ?: job
        }
    }

    private suspend fun downloadMagnet(
        tab: ProviderTab,
        job: TransferJob,
        magnet: String,
        cancelled: () -> Boolean,
    ): TransferJob {
        val apiKey = secrets.read(tab.credentialRef)
            ?: throw ProviderException(ProviderErrorCode.AUTHENTICATION, "Resolver credential is missing")
        return ProviderMagnetDownload.downloadAll(
            apiKey = apiKey,
            magnet = magnet,
            job = job,
            title = job.title,
            resolver = resolver,
            downloader = downloader,
            cancelled = cancelled,
            persist = { persist(it) },
            onProgress = { done, total -> publishProgress(job.jobId, done, total) },
        )
    }

    private fun scrapeMagnet(pageUrl: String): String {
        if (pageUrl.isBlank()) return ""
        val html = runCatching { feedClient.fetchText(pageUrl) }.getOrDefault("")
        return WordpressMagnets.first(html)
    }

    suspend fun completePortableInstall(
        job: TransferJob,
        selectedExe: File,
        policy: CleanupPolicy,
        confirmed: Boolean = false,
    ): TransferJob {
        val installer = File(job.finalPath)
        val hash = if (installer.isFile) StreamingHasher.sha256(installer) else job.actualSha256.orEmpty()
        if (installer.isFile && !StreamingHasher.matches(job.expectedSha256, hash)) {
            throw ProviderException(ProviderErrorCode.HASH_MISMATCH, "Installer hash did not match")
        }
        val destination = File(job.destinationPath.ifBlank { selectedExe.parent.orEmpty() })
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
            installerOwnedByJob = !installer.exists() || installer.absolutePath == job.finalPath,
        )
        if (!InstallerCleanup.shouldSkip(job, destination) && (confirmed || decision.canDelete)) {
            InstallerCleanup.remove(job, destination, stagingRoot)
        }
        return persist(
            job.copy(
                state = TransferState.READY,
                destinationPath = destination.absolutePath,
                executablePath = selectedExe.absolutePath,
                actualSha256 = hash,
                errorMessage = "",
                errorCode = null,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun extractPortable(job: TransferJob): TransferJob = extractArchive(job)

    suspend fun extractArchive(job: TransferJob): TransferJob = withContext(Dispatchers.IO) {
        val archive = File(job.finalPath)
        val dest = File(stagingRoot, "${job.jobId}-extract")
        runCatching {
            app.gamenative.mods.ModArchiveExtractor.extract(archive, dest)
        }.getOrElse { error ->
            dest.deleteRecursively()
            throw ProviderException(
                ProviderErrorCode.MALFORMED_RESPONSE,
                error.message ?: "Could not extract archive",
            )
        }
        persist(job.copy(state = TransferState.INSTALLING, destinationPath = dest.absolutePath))
    }

    suspend fun markFailed(job: TransferJob, message: String): TransferJob = persist(
        job.copy(state = TransferState.FAILED, errorCode = ProviderErrorCode.NETWORK, errorMessage = message),
    )

    private suspend fun resolve(
        tab: ProviderTab,
        job: TransferJob,
        candidates: List<String>,
        pageUrl: String,
    ): TransferJob {
        val apiKey = secrets.read(tab.credentialRef)
            ?: throw ProviderException(ProviderErrorCode.AUTHENTICATION, "Resolver credential is missing")
        persist(job.copy(state = TransferState.RESOLVING))
        val links = unlockTargets(job.selectedLink, candidates, pageUrl, tab.feedUrl)
        Timber.tag("ProviderTransfer").i("Unlocking ${links.size} hoster(s) for ${job.title}")
        return unlockFirst(apiKey, job, links)
    }

    private suspend fun unlockFirst(
        apiKey: String,
        job: TransferJob,
        links: List<String>,
    ): TransferJob {
        var lastError: ProviderException? = null
        for (link in links) {
            try {
                return persistResolved(job, link, resolver.resolve(apiKey, link))
            } catch (error: ProviderException) {
                if (isFatalResolver(error)) throw error
                lastError = error
            }
        }
        throw lastError ?: ProviderException(
            ProviderErrorCode.UNAVAILABLE_LINK,
            "No file hoster link in this post",
        )
    }

    private fun isFatalResolver(error: ProviderException): Boolean =
        error.code == ProviderErrorCode.AUTHENTICATION || error.code == ProviderErrorCode.RATE_LIMIT

    private fun unlockTargets(
        selected: String,
        candidates: List<String>,
        pageUrl: String,
        feedUrl: String,
    ): List<String> {
        val ranked = HosterAllowlist.filter(
            WordpressMetadata.rankLinks((candidates + selected).filter { it.isNotBlank() }),
            feedUrl,
        )
        if (ranked.isNotEmpty()) return ranked
        if (pageUrl.isBlank()) return emptyList()
        val html = runCatching { feedClient.fetchText(pageUrl) }.getOrDefault("")
        return HosterAllowlist.filter(WordpressMetadata.httpsLinks(html), feedUrl)
    }

    private suspend fun persistResolved(
        job: TransferJob,
        selectedLink: String,
        resolved: ResolvedDownload,
    ): TransferJob {
        val partial = File(stagingRoot, "${job.jobId}/${resolved.filename}.partial")
        return persist(
            job.copy(
                state = TransferState.DOWNLOADING,
                selectedLink = selectedLink,
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
