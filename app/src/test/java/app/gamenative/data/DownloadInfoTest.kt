package app.gamenative.data

import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadInfoTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `progress listeners are sampled and still publish completion`() {
        var count = 0
        val info = newDownloadInfo()
        info.addProgressListener { count++ }
        info.setProgress(0.1f)
        info.setProgress(0.2f)
        assertEquals(1, count)
        Thread.sleep(DownloadInfo.PROGRESS_EMIT_INTERVAL_MS + 20L)
        info.setProgress(0.3f)
        assertEquals(2, count)
        info.setProgress(1f)
        assertEquals(3, count)
    }

    @Test
    fun `post install sync state is tracked independently`() {
        val info = DownloadInfo(
            jobCount = 1,
            gameId = 123,
            downloadingAppIds = CopyOnWriteArrayList(),
        )

        assertFalse(info.isPostInstallSyncing())

        info.setPostInstallSyncing(true)

        assertTrue(info.isPostInstallSyncing())
    }

    @Test
    fun `cancel clears post install sync state`() {
        val info = DownloadInfo(
            jobCount = 1,
            gameId = 123,
            downloadingAppIds = CopyOnWriteArrayList(),
        )

        info.setPostInstallSyncing(true)
        info.cancel()

        assertFalse(info.isPostInstallSyncing())
        assertFalse(info.isActive())
    }

    @Test
    fun `persistence is debounced and keeps the latest value`() {
        val info = newDownloadInfo().apply { persistenceDebounceMs = 40L }
        info.updateBytesDownloaded(100L)
        info.persistBytesDownloaded(tempFolder.root.absolutePath)
        awaitPersistedValue(info, 100L)

        info.updateBytesDownloaded(50L)
        info.persistBytesDownloaded(tempFolder.root.absolutePath)
        info.updateBytesDownloaded(100L)
        info.persistBytesDownloaded(tempFolder.root.absolutePath)

        assertEquals(100L, info.loadPersistedBytesDownloaded(tempFolder.root.absolutePath))
        awaitPersistedValue(info, 250L)
    }

    @Test
    fun `clearing persistence prevents a pending write from recreating the file`() {
        val info = newDownloadInfo().apply { persistenceDebounceMs = 60L }
        info.updateBytesDownloaded(100L)
        info.persistBytesDownloaded(tempFolder.root.absolutePath)
        awaitPersistedValue(info, 100L)

        info.updateBytesDownloaded(200L)
        info.persistBytesDownloaded(tempFolder.root.absolutePath)
        info.clearPersistedBytesDownloaded(tempFolder.root.absolutePath)

        Thread.sleep(120L)
        assertEquals(0L, info.loadPersistedBytesDownloaded(tempFolder.root.absolutePath))
    }

    private fun newDownloadInfo() = DownloadInfo(
        jobCount = 1,
        gameId = 123,
        downloadingAppIds = CopyOnWriteArrayList(),
    )

    private fun awaitPersistedValue(info: DownloadInfo, expected: Long) {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (System.nanoTime() < deadline) {
            if (info.loadPersistedBytesDownloaded(tempFolder.root.absolutePath) == expected) return
            Thread.sleep(5L)
        }
        assertEquals(expected, info.loadPersistedBytesDownloaded(tempFolder.root.absolutePath))
    }
}
