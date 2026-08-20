package app.gamenative.provider

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallerWineEnvTest {
    @Test
    fun `detects setup and msi names as installers`() {
        assertEquals("Box64", InstallerWineEnv.EMULATOR)
        assertTrue(InstallerWineEnv.isInstallerName("setup.exe"))
        assertTrue(InstallerWineEnv.isInstallerName("A:\\Install.exe"))
        assertTrue(InstallerWineEnv.isInstallerName("game.msi"))
        assertFalse(InstallerWineEnv.isInstallerName("DarkestDungeon.exe"))
    }

    @Test
    fun `treats a FitGirl pack as an installer even if the exe name is empty`() {
        val root = kotlin.io.path.createTempDirectory("installer-env").toFile()
        File(root, "setup.exe").writeBytes(ByteArray(8))
        File(root, "fg-01.bin").writeBytes(byteArrayOf(0x41, 0x72, 0x43, 0x01, 0, 0, 0, 0))
        assertTrue(InstallerWineEnv.isInstaller("", root))
        root.deleteRecursively()
    }
}
