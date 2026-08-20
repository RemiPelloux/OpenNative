package app.gamenative.provider

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutableDiscoveryTest {
    @Test
    fun `ignores setup and FitGirl checksum tools`() {
        val root = kotlin.io.path.createTempDirectory("exe-discovery").toFile()
        val pack = File(root, "Darkest Dungeon FitGirl Repack").also { it.mkdirs() }
        File(pack, "setup.exe").writeBytes(ByteArray(8))
        File(pack, "MD5").mkdirs()
        File(pack, "MD5/QuickSFV.EXE").writeBytes(ByteArray(8))
        assertTrue(ExecutableDiscovery.discover(root).isEmpty())
        File(pack, "DarkestDungeon.exe").writeBytes(ByteArray(8))
        val found = ExecutableDiscovery.discover(root)
        assertEquals(1, found.size)
        assertEquals("DarkestDungeon.exe", found.single().name)
        root.deleteRecursively()
    }
}
