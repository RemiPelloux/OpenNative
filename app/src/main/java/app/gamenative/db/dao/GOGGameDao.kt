package app.gamenative.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.gamenative.data.GOGGame
import kotlinx.coroutines.flow.Flow

/**
 * DAO for GOG games in the Room database
 */
@Dao
interface GOGGameDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(game: GOGGame)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<GOGGame>)

    @Update
    suspend fun update(game: GOGGame)

    @Delete
    suspend fun delete(game: GOGGame)

    @Query("DELETE FROM gog_games WHERE id = :gameId")
    suspend fun deleteById(gameId: String)

    @Query("SELECT * FROM gog_games WHERE id = :gameId")
    suspend fun getById(gameId: String): GOGGame?

    @Query("SELECT * FROM gog_games WHERE id IN (:gameIds)")
    suspend fun getGamesByIds(gameIds: List<String>): List<GOGGame>

    @Query("SELECT * FROM gog_games WHERE exclude = 0 ORDER BY title ASC")
    fun getAll(): Flow<List<GOGGame>>

    @Query("SELECT * FROM gog_games WHERE exclude = 0 ORDER BY title ASC")
    suspend fun getAllAsList(): List<GOGGame>

    @Query("SELECT * FROM gog_games WHERE is_installed = :isInstalled AND exclude = 0 ORDER BY title ASC")
    fun getByInstallStatus(isInstalled: Boolean): Flow<List<GOGGame>>

    /** Returns all installed GOG games, excluding excluded entries, sorted by title. */
    @Query("SELECT * FROM gog_games WHERE is_installed = 1 AND exclude = 0 ORDER BY title ASC")
    suspend fun getInstalledGames(): List<GOGGame>

    @Query("SELECT * FROM gog_games WHERE is_installed = 0 AND exclude = 0")
    suspend fun getNonInstalledGames(): List<GOGGame>

    @Query("SELECT * FROM gog_games WHERE exclude = 0 AND title LIKE '%' || :searchQuery || '%' ORDER BY title ASC")
    fun searchByTitle(searchQuery: String): Flow<List<GOGGame>>

    @Query("DELETE FROM gog_games WHERE is_installed = 0")
    suspend fun deleteAllNonInstalledGames()

    @Query("SELECT COUNT(*) FROM gog_games WHERE exclude = 0")
    fun getCount(): Flow<Int>

    @Query("SELECT id FROM gog_games")
    suspend fun getAllGameIdsIncludingExcluded(): List<String>

    @Query("SELECT id FROM gog_games WHERE exclude = 0 AND vertical_cover_url = ''")
    suspend fun getGameIdsMissingVerticalCover(): List<String>

    @Query("UPDATE gog_games SET vertical_cover_url = :url WHERE id = :gameId")
    suspend fun updateVerticalCoverUrl(gameId: String, url: String)

    @Query(
        "UPDATE gog_games " +
            "SET install_path = :newPrefix || substr(install_path, length(:oldPrefix) + 1) " +
            "WHERE install_path LIKE :oldPrefix || '%'",
    )
    suspend fun replaceInstallPathPrefix(oldPrefix: String, newPrefix: String): Int

    /**
     * Upsert GOG games while preserving install status and paths
     * This is useful when refreshing the library from GOG API
     */
    @Transaction
    suspend fun upsertPreservingInstallStatus(games: List<GOGGame>) {
        if (games.isEmpty()) return

        val existingById = getGamesByIds(games.map { it.id }).associateBy { it.id }
        val gamesToInsert = games.map { newGame ->
            val existingGame = existingById[newGame.id]
            if (existingGame != null) {
                newGame.copy(
                    isInstalled = existingGame.isInstalled,
                    installPath = existingGame.installPath,
                    installSize = existingGame.installSize,
                    lastPlayed = existingGame.lastPlayed,
                    playTime = existingGame.playTime,
                )
            } else {
                newGame
            }
        }
        insertAll(gamesToInsert)
    }
}
