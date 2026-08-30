package app.gamenative.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.gamenative.db.entity.ProviderTransferJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderTransferJobDao {
    @Query("SELECT * FROM provider_transfer_jobs WHERE tab_id = :tabId ORDER BY updated_at DESC")
    fun observeForTab(tabId: String): Flow<List<ProviderTransferJobEntity>>

    @Query("SELECT * FROM provider_transfer_jobs ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ProviderTransferJobEntity>>

    @Query("SELECT * FROM provider_transfer_jobs WHERE job_id = :jobId LIMIT 1")
    suspend fun getById(jobId: String): ProviderTransferJobEntity?

    @Query("SELECT * FROM provider_transfer_jobs WHERE tab_id = :tabId AND item_id = :itemId AND state = 'FAILED'")
    suspend fun getFailedForItem(tabId: String, itemId: String): List<ProviderTransferJobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProviderTransferJobEntity)

    @Query("DELETE FROM provider_transfer_jobs WHERE tab_id = :tabId")
    suspend fun deleteForTab(tabId: String)
}
