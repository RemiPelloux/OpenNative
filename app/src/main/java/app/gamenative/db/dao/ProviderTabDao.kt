package app.gamenative.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.gamenative.db.entity.ProviderTabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderTabDao {
    @Query("SELECT * FROM provider_tabs ORDER BY position ASC")
    fun observeAll(): Flow<List<ProviderTabEntity>>

    @Query("SELECT * FROM provider_tabs ORDER BY position ASC")
    suspend fun getAll(): List<ProviderTabEntity>

    @Query("SELECT * FROM provider_tabs WHERE tab_id = :id LIMIT 1")
    suspend fun getById(id: String): ProviderTabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProviderTabEntity)

    @Update
    suspend fun update(entity: ProviderTabEntity)

    @Query("DELETE FROM provider_tabs WHERE tab_id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM provider_tabs")
    suspend fun maxPosition(): Int
}
