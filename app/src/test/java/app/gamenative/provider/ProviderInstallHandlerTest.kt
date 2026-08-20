package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderInstallHandlerTest {
    @Test
    fun `finds zip rar or 7z in a downloaded folder`() {
        val root = kotlin.io.path.createTempDirectory("skidrow-pack").toFile()
        java.io.File(root, "readme.txt").writeText("x")
        val zip = java.io.File(root, "game.zip")
        zip.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00))
        assertEquals(zip.absolutePath, ProviderInstallHandler.findArchive(root)?.absolutePath)
        zip.delete()
        val rar = java.io.File(root, "game.rar")
        rar.writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00, 0x00))
        assertEquals(rar.absolutePath, ProviderInstallHandler.findArchive(root)?.absolutePath)
        root.deleteRecursively()
    }

    @Test
    fun `fitgirl setup folder is not treated as an archive`() {
        val root = kotlin.io.path.createTempDirectory("fitgirl-pack").toFile()
        java.io.File(root, "setup.exe").writeBytes(byteArrayOf(0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        java.io.File(root, "fg-01.bin").writeBytes(ByteArray(8))
        assertNull(ProviderInstallHandler.findArchive(root))
        root.deleteRecursively()
    }
}
