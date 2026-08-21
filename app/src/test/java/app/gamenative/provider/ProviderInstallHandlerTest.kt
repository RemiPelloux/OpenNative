package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderInstallHandlerTest {
    @Test
    fun `skidrow extracts always delete the archive`() {
        val skidrow = ProviderTab(
            id = "skidrow",
            name = "Skidrow",
            position = 1,
            feedUrl = "https://feeds.feedburner.com/SkidrowReloadedGames",
            cleanupPolicy = CleanupPolicy.KEEP,
        )
        val fitgirl = ProviderTab(
            id = "fitgirl",
            name = "FitGirl",
            position = 0,
            feedUrl = "https://fitgirl-repacks.site/wp-json/wp/v2/posts",
            cleanupPolicy = CleanupPolicy.KEEP,
        )
        assertEquals(true, ProviderInstallHandler.shouldDeleteArchive(skidrow))
        assertEquals(false, ProviderInstallHandler.shouldDeleteArchive(fitgirl))
        assertEquals(
            true,
            ProviderInstallHandler.shouldDeleteArchive(
                fitgirl.copy(cleanupPolicy = CleanupPolicy.DELETE_AFTER_VERIFIED_INSTALL),
            ),
        )
    }

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
