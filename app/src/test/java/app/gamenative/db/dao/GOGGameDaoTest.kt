package app.gamenative.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gamenative.data.GOGGame
import app.gamenative.db.PluviaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GOGGameDaoTest {
    private lateinit var database: PluviaDatabase
    private lateinit var dao: GOGGameDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PluviaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.gogGameDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertPreservingInstallStatus_updatesBatchAndKeepsLocalState() = runBlocking {
        dao.insertAll(
            listOf(
                GOGGame(
                    id = "1",
                    title = "Old title",
                    isInstalled = true,
                    installPath = "/games/one",
                    installSize = 42L,
                    lastPlayed = 100L,
                    playTime = 200L,
                ),
            ),
        )

        dao.upsertPreservingInstallStatus(
            listOf(
                GOGGame(id = "1", title = "Fresh metadata"),
                GOGGame(id = "2", title = "New game"),
            ),
        )

        val existing = dao.getById("1")!!
        assertEquals("Fresh metadata", existing.title)
        assertEquals(true, existing.isInstalled)
        assertEquals("/games/one", existing.installPath)
        assertEquals(42L, existing.installSize)
        assertEquals(100L, existing.lastPlayed)
        assertEquals(200L, existing.playTime)
        assertEquals("New game", dao.getById("2")?.title)
    }

    @Test
    fun replaceInstallPathPrefix_updatesOnlyMatchingPrefix() = runBlocking {
        dao.insertAll(
            listOf(
                GOGGame(id = "1", installPath = "/old/root/GOG/one"),
                GOGGame(id = "2", installPath = "/other/root/GOG/two"),
            ),
        )

        val updated = dao.replaceInstallPathPrefix("/old/root/", "/new/root/")

        assertEquals(1, updated)
        assertEquals("/new/root/GOG/one", dao.getById("1")?.installPath)
        assertEquals("/other/root/GOG/two", dao.getById("2")?.installPath)
    }
}
