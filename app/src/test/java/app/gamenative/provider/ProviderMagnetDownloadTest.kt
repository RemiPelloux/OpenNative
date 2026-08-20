package app.gamenative.provider

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderMagnetDownloadTest {
    @Test
    fun `writes magnet files as siblings in the title folder`() {
        val dest = kotlin.io.path.createTempDirectory("magnet-flat").toFile()
        val setup = ProviderMagnetDownload.confinedTarget(
            dest,
            MagnetRemoteFile("Darkest Dungeon [FitGirl Repack]/setup.exe", "https://example.com/s"),
        )
        val bin = ProviderMagnetDownload.confinedTarget(
            dest,
            MagnetRemoteFile("Darkest Dungeon [FitGirl Repack]/fg-01.bin", "https://example.com/b"),
        )
        assertEquals("setup.exe", setup.name)
        assertEquals("fg-01.bin", bin.name)
        assertEquals(dest.canonicalFile, setup.parentFile?.canonicalFile)
        assertEquals(dest.canonicalFile, bin.parentFile?.canonicalFile)
        dest.deleteRecursively()
    }

    @Test
    fun `promotes an already downloaded nested file into the title folder`() {
        val dest = kotlin.io.path.createTempDirectory("magnet-promote").toFile()
        val nested = File(dest, "darkest-dungeon-fitgirl-repack/setup.exe")
        nested.parentFile?.mkdirs()
        nested.writeBytes(ByteArray(16))
        val target = ProviderMagnetDownload.confinedTarget(
            dest,
            MagnetRemoteFile("Darkest Dungeon [FitGirl Repack]/setup.exe", "https://example.com/s", 16L),
        )
        assertEquals("setup.exe", target.name)
        assertEquals(dest.canonicalFile, target.parentFile?.canonicalFile)
        assertTrue(target.exists())
        dest.deleteRecursively()
    }
}
