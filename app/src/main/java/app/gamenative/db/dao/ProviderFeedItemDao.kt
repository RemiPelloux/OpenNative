package app.gamenative.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.gamenative.db.entity.ProviderFeedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderFeedItemDao {
    @Query("SELECT * FROM provider_feed_items WHERE tab_id = :tabId ORDER BY published_at DESC, title ASC")
    fun observeForTab(tabId: String): Flow<List<ProviderFeedItemEntity>>

    @Query(
        "SELECT * FROM provider_feed_items WHERE tab_id = :tabId " +
            "AND (title LIKE :query OR description LIKE :query) " +
            "ORDER BY published_at DESC, title ASC",
    )
    suspend fun search(tabId: String, query: String): List<ProviderFeedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ProviderFeedItemEntity>)

    @Query("DELETE FROM provider_feed_items WHERE tab_id = :tabId")
    suspend fun deleteForTab(tabId: String)

    @Transaction
    suspend fun replacePage(tabId: String, items: List<ProviderFeedItemEntity>, replaceAll: Boolean) {
        if (replaceAll) deleteForTab(tabId)
        if (items.isNotEmpty()) upsertAll(items)
    }
}
