package app.gamenative.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.gamenative.db.entity.ProviderInstallReceiptEntity

@Dao
interface ProviderInstallReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProviderInstallReceiptEntity)

    @Query("SELECT * FROM provider_install_receipts WHERE job_id = :jobId LIMIT 1")
    suspend fun getByJob(jobId: String): ProviderInstallReceiptEntity?
}
