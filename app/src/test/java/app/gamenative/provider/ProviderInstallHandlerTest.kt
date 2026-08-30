package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `finds zip rar 7z or iso in a downloaded folder`() {
        val root = kotlin.io.path.createTempDirectory("skidrow-pack").toFile()
        java.io.File(root, "readme.txt").writeText("x")
        val zip = java.io.File(root, "game.zip")
        zip.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00))
        assertEquals(zip.absolutePath, ProviderInstallHandler.findArchive(root)?.absolutePath)
        zip.delete()
        val rar = java.io.File(root, "game.rar")
        rar.writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00, 0x00))
        assertEquals(rar.absolutePath, ProviderInstallHandler.findArchive(root)?.absolutePath)
        rar.delete()
        val iso = java.io.File(root, "game.iso")
        val isoBytes = ByteArray(16 * 2048 + 7)
        "CD001".toByteArray(Charsets.US_ASCII).copyInto(isoBytes, destinationOffset = 16 * 2048 + 1)
        iso.writeBytes(isoBytes)
        assertEquals(iso.absolutePath, ProviderInstallHandler.findArchive(root)?.absolutePath)
        root.deleteRecursively()
    }

    @Test
    fun `archive discovery uses signatures and selects first multipart volume`() {
        val root = kotlin.io.path.createTempDirectory("nested-archives").toFile()
        val second = java.io.File(root, "game.part02.rar")
        val first = java.io.File(root, "game.part01.rar")
        val disguised = java.io.File(root, "payload.data")
        val rarHeader = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00, 0x00)
        second.writeBytes(rarHeader)
        first.writeBytes(rarHeader)
        disguised.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00))

        val archives = ProviderInstallHandler.findArchives(root)

        assertEquals(first.absolutePath, archives.first().absolutePath)
        assertTrue(archives.any { it.absolutePath == disguised.absolutePath })
        root.deleteRecursively()
    }

    @Test
    fun `publishing extraction replaces only an empty destination`() {
        val root = kotlin.io.path.createTempDirectory("publish-extraction").toFile()
        val source = java.io.File(root, "source").apply { mkdirs() }
        java.io.File(source, "game.exe").writeText("game")
        val destination = java.io.File(root, "game").apply { mkdirs() }

        ProviderInstallHandler.publishExtracted(source.absolutePath, destination, "job-1")

        assertEquals("game", java.io.File(destination, "game.exe").readText())
        assertFalse(java.io.File(root, ".game.installing-job-1").exists())
        root.deleteRecursively()
    }

    @Test
    fun `publishing extraction preserves an existing populated destination`() {
        val root = kotlin.io.path.createTempDirectory("publish-conflict").toFile()
        val source = java.io.File(root, "source").apply { mkdirs() }
        java.io.File(source, "new.exe").writeText("new")
        val destination = java.io.File(root, "game").apply { mkdirs() }
        val existing = java.io.File(destination, "save.dat").apply { writeText("keep") }

        val result = runCatching {
            ProviderInstallHandler.publishExtracted(source.absolutePath, destination, "job-2")
        }

        assertTrue(result.isFailure)
        assertEquals("keep", existing.readText())
        assertFalse(java.io.File(root, ".game.installing-job-2").exists())
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

    @Test
    fun `skidrow installer-only payload launches setup but portable payload does not`() {
        val skidrow = ProviderTab(
            id = "skidrow",
            name = "Skidrow",
            position = 1,
            feedUrl = "https://feeds.feedburner.com/SkidrowReloadedGames",
        )
        val root = kotlin.io.path.createTempDirectory("skidrow-installer").toFile()
        java.io.File(root, "SETUP.exe").writeBytes(byteArrayOf(0x4D, 0x5A, 0x00, 0x00))

        assertTrue(ProviderInstallHandler.shouldLaunchSetup(skidrow, root))

        java.io.File(root, "Divinum.exe").writeBytes(byteArrayOf(0x4D, 0x5A, 0x00, 0x00))
        assertFalse(ProviderInstallHandler.shouldLaunchSetup(skidrow, root))
        root.deleteRecursively()
    }
}
