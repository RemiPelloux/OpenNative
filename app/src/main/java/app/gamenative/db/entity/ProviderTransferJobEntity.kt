package app.gamenative.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.gamenative.provider.ProviderErrorCode
import app.gamenative.provider.TransferJob
import app.gamenative.provider.TransferState

@Entity(tableName = "provider_transfer_jobs", indices = [Index("tab_id"), Index("state")])
data class ProviderTransferJobEntity(
    @PrimaryKey
    @ColumnInfo("job_id")
    val jobId: String,
    @ColumnInfo("tab_id")
    val tabId: String,
    @ColumnInfo("item_id")
    val itemId: String,
    @ColumnInfo("title")
    val title: String,
    @ColumnInfo("state")
    val state: String,
    @ColumnInfo("selected_link")
    val selectedLink: String,
    @ColumnInfo("resolved_url")
    val resolvedUrl: String,
    @ColumnInfo("filename")
    val filename: String,
    @ColumnInfo("partial_path")
    val partialPath: String,
    @ColumnInfo("final_path")
    val finalPath: String,
    @ColumnInfo("destination_path")
    val destinationPath: String,
    @ColumnInfo("executable_path")
    val executablePath: String,
    @ColumnInfo("bytes_downloaded")
    val bytesDownloaded: Long,
    @ColumnInfo("bytes_total")
    val bytesTotal: Long,
    @ColumnInfo("expected_sha256")
    val expectedSha256: String?,
    @ColumnInfo("actual_sha256")
    val actualSha256: String?,
    @ColumnInfo("error_code")
    val errorCode: String?,
    @ColumnInfo("error_message")
    val errorMessage: String,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
    @ColumnInfo("progress_percent")
    val progressPercent: Int,
) {
    fun toDomain(): TransferJob = TransferJob(
        jobId = jobId,
        tabId = tabId,
        itemId = itemId,
        title = title,
        state = TransferState.entries.find { it.name == state } ?: TransferState.FAILED,
        selectedLink = selectedLink,
        resolvedUrl = resolvedUrl,
        filename = filename,
        partialPath = partialPath,
        finalPath = finalPath,
        destinationPath = destinationPath,
        executablePath = executablePath,
        bytesDownloaded = bytesDownloaded,
        bytesTotal = bytesTotal,
        expectedSha256 = expectedSha256,
        actualSha256 = actualSha256,
        errorCode = errorCode?.let { code -> ProviderErrorCode.entries.find { it.name == code } },
        errorMessage = errorMessage,
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = updatedAt,
        progressPercent = progressPercent,
    )

    companion object {
        fun fromDomain(job: TransferJob): ProviderTransferJobEntity = ProviderTransferJobEntity(
            jobId = job.jobId,
            tabId = job.tabId,
            itemId = job.itemId,
            title = job.title,
            state = job.state.name,
            selectedLink = job.selectedLink,
            resolvedUrl = job.resolvedUrl,
            filename = job.filename,
            partialPath = job.partialPath,
            finalPath = job.finalPath,
            destinationPath = job.destinationPath,
            executablePath = job.executablePath,
            bytesDownloaded = job.bytesDownloaded,
            bytesTotal = job.bytesTotal,
            expectedSha256 = job.expectedSha256,
            actualSha256 = job.actualSha256,
            errorCode = job.errorCode?.name,
            errorMessage = job.errorMessage,
            createdAt = job.createdAtEpochMs,
            updatedAt = job.updatedAtEpochMs,
            progressPercent = job.progressPercent,
        )
    }
}
