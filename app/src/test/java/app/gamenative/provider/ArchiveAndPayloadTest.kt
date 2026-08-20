package app.gamenative.provider

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveAndPayloadTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `rejects zip traversal`() {
        val zip = temp.newFile("escape.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("../secret.txt"))
            out.write("x".toByteArray())
            out.closeEntry()
        }
        try {
            ArchiveInspector.inspectZip(zip)
            throw AssertionError("expected failure")
        } catch (error: ProviderException) {
            assertEquals(ProviderErrorCode.PATH_ESCAPE, error.code)
        }
    }

    @Test
    fun `confines extract paths`() {
        val staging = temp.newFolder("staging")
        val target = ArchiveInspector.confinedPath(staging, "game/play.exe")
        assertTrue(target.canonicalPath.startsWith(staging.canonicalPath))
    }

    @Test
    fun `classifies zip as portable archive`() {
        val zip = temp.newFile("game.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("play.exe"))
            out.write(byteArrayOf(0x4D, 0x5A, 0, 0))
            out.closeEntry()
        }
        assertEquals(PayloadKind.PORTABLE_ARCHIVE, PayloadClassifier.classify(zip))
    }

    @Test
    fun `rejects exe extension without pe signature`() {
        val fake = temp.newFile("setup.exe")
        fake.writeBytes(byteArrayOf(1, 2, 3, 4))
        try {
            PayloadClassifier.classify(fake)
            throw AssertionError("expected failure")
        } catch (error: ProviderException) {
            assertEquals(ProviderErrorCode.MALFORMED_RESPONSE, error.code)
        }
    }

    @Test
    fun `installer command is safe for msi`() {
        val plan = InstallerCommand.plan(PayloadKind.WINDOWS_MSI, "C:\\setup.msi", "C:\\")
        assertEquals("msiexec", plan.executable)
        assertEquals(listOf("/i", "C:\\setup.msi"), plan.arguments)
    }

    @Test
    fun `discover skips uninstallers`() {
        val dest = temp.newFolder("dest")
        File(dest, "Game.exe").writeText("exe")
        File(dest, "unins000.exe").writeText("unins")
        val found = ExecutableDiscovery.discover(dest)
        assertEquals(listOf("Game.exe"), found.map { it.name })
    }
}
