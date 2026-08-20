package app.gamenative.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.gamenative.provider.InstallReceipt

@Entity(tableName = "provider_install_receipts", indices = [Index("job_id")])
data class ProviderInstallReceiptEntity(
    @PrimaryKey
    @ColumnInfo("receipt_id")
    val receiptId: String,
    @ColumnInfo("job_id")
    val jobId: String,
    @ColumnInfo("tab_id")
    val tabId: String,
    @ColumnInfo("item_id")
    val itemId: String,
    @ColumnInfo("installer_hash")
    val installerHash: String,
    @ColumnInfo("container_id")
    val containerId: String,
    @ColumnInfo("destination_path")
    val destinationPath: String,
    @ColumnInfo("executable_path")
    val executablePath: String,
    @ColumnInfo("cleanup_result")
    val cleanupResult: String,
    @ColumnInfo("created_at")
    val createdAt: Long,
) {
    fun toDomain(): InstallReceipt = InstallReceipt(
        receiptId = receiptId,
        jobId = jobId,
        tabId = tabId,
        itemId = itemId,
        installerHash = installerHash,
        containerId = containerId,
        destinationPath = destinationPath,
        executablePath = executablePath,
        cleanupResult = cleanupResult,
        createdAtEpochMs = createdAt,
    )

    companion object {
        fun fromDomain(receipt: InstallReceipt): ProviderInstallReceiptEntity =
            ProviderInstallReceiptEntity(
                receiptId = receipt.receiptId,
                jobId = receipt.jobId,
                tabId = receipt.tabId,
                itemId = receipt.itemId,
                installerHash = receipt.installerHash,
                containerId = receipt.containerId,
                destinationPath = receipt.destinationPath,
                executablePath = receipt.executablePath,
                cleanupResult = receipt.cleanupResult,
                createdAt = receipt.createdAtEpochMs,
            )
    }
}
