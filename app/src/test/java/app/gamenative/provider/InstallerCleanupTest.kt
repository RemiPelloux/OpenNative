package app.gamenative.provider

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallerCleanupTest {
    @Test
    fun `confirmed install deletes installer and staging copy`() {
        val root = File(createTempDir(), "case").also { it.mkdirs() }
        val staging = File(root, "staging").also { it.mkdirs() }
        val game = File(root, "game").also { it.mkdirs() }
        val installer = File(staging, "setup.exe").also { it.writeText("exe") }
        val stagedExtract = File(staging, "job-1").also { it.mkdirs() }
        File(game, "setup.exe").writeText("copy")
        File(game, "Game.exe").writeText("game")
        val job = TransferJob(
            jobId = "job-1",
            tabId = "tab",
            itemId = "item",
            title = "Game",
            selectedLink = "https://example.com/setup.exe",
            finalPath = installer.absolutePath,
            destinationPath = stagedExtract.absolutePath,
        )
        InstallerCleanup.remove(job, game, staging)
        assertFalse(installer.exists())
        assertFalse(File(game, "setup.exe").exists())
        assertFalse(stagedExtract.exists())
        assertTrue(File(game, "Game.exe").exists())
    }
}
