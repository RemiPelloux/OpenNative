package app.gamenative.provider

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallerGameDirTest {
    @Test
    fun `maps every installer onto D games slug`() {
        assertEquals("D:\\games", InstallerGameDir.WINE_ROOT)
        assertEquals(
            "D:\\games\\darkest-dungeon-the-collector-s-edition-v27760-7-dlcs-bonuses",
            InstallerGameDir.winePath("Darkest Dungeon The Collector's Edition v27760 + 7 DLCs/Bonuses"),
        )
        assertEquals(
            "/DIR=\"D:\\games\\darkest-dungeon-fitgirl-repack\" /NORESTART",
            InstallerGameDir.execArgs("Darkest Dungeon [FitGirl Repack]"),
        )
    }

    @Test
    fun `creates the host folder under Downloads games`() {
        val downloads = kotlin.io.path.createTempDirectory("downloads").toFile()
        val dest = InstallerGameDir.ensureHost("Darkest Dungeon [FitGirl Repack]", downloads)
        assertEquals(File(downloads, "games/darkest-dungeon-fitgirl-repack").canonicalFile, dest.canonicalFile)
        assertTrue(dest.isDirectory)
        downloads.deleteRecursively()
    }

    @Test
    fun `deletes the pack only when the game lives somewhere else`() {
        val root = kotlin.io.path.createTempDirectory("cleanup-pack").toFile()
        val pack = File(root, "pack").also { it.mkdirs() }
        val game = File(root, "games/title").also { it.mkdirs() }
        File(pack, "setup.exe").writeBytes(ByteArray(8))
        File(game, "DarkestDungeon.exe").writeBytes(ByteArray(8))
        InstallerCleanup.removePack(pack, game)
        assertFalse(pack.exists())
        assertTrue(game.exists())
        InstallerCleanup.removePack(game, game)
        assertTrue(game.exists())
        root.deleteRecursively()
    }
}
