package app.gamenative.mods

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile as Zip4jFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModArchiveExtractorTest {
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = createTempDirectory("nexus_archive_test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun extractZip_blocksZipSlip() = runBlocking {
        val archive = File(tempDir, "bad.zip")
        zip(archive, "../escape.txt" to "bad")

        val result = runCatching {
            ModArchiveExtractor.extract(archive, File(tempDir, "out"))
        }

        assertTrue(result.isFailure)
        assertTrue(!File(tempDir.parentFile, "escape.txt").exists())
    }

    @Test
    fun extractZip_blocksAbsolutePaths() = runBlocking {
        val archive = File(tempDir, "bad.zip")
        zip(archive, "/tmp/escape.txt" to "bad")

        val result = runCatching {
            ModArchiveExtractor.extract(archive, File(tempDir, "out"))
        }

        assertTrue(result.isFailure)
        assertTrue(!File(tempDir, "out/tmp/escape.txt").exists())
    }

    @Test
    fun extractZip_rejectsPathBeyondImportDepthLimit() = runBlocking {
        val archive = File(tempDir, "too-deep.zip")
        val tooDeep = List(66) { "d$it" }.joinToString("/") + "/file.txt"
        zip(archive, tooDeep to "data")

        val result = runCatching {
            ModArchiveExtractor.extract(archive, File(tempDir, "out-too-deep"))
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun extractZip_rejectsOverlongRelativePath() = runBlocking {
        val archive = File(tempDir, "too-long.zip")
        zip(archive, "d/${"x".repeat(1_024)}.txt" to "data")

        val result = runCatching {
            ModArchiveExtractor.extract(archive, File(tempDir, "out-too-long"))
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun extractZip_rejectsOverlongRawPathBeforeNormalization() = runBlocking {
        val archive = File(tempDir, "too-long-raw.zip")
        zip(archive, "${"./".repeat(513)}file.txt" to "data")

        val result = runCatching {
            ModArchiveExtractor.extract(archive, File(tempDir, "out-too-long-raw"))
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun extractZip_listsEntries() = runBlocking {
        val archive = File(tempDir, "mod.zip")
        zip(
            archive,
            "Wrapper/Data/plugin.esp" to "plugin",
            "Wrapper/readme.txt" to "readme",
        )

        val result = ModArchiveExtractor.extract(archive, File(tempDir, "out"))

        assertTrue(File(result.destination, "Wrapper/Data/plugin.esp").isFile)
        assertTrue(result.entries.any { it.path == "Wrapper/Data/plugin.esp" })
    }

    @Test
    fun extractZip_supportsPartSuffixFromTemporaryDownload() = runBlocking {
        val archive = File(tempDir, "mod.zip.part")
        zip(archive, "Data/plugin.esp" to "plugin")

        val result = ModArchiveExtractor.extract(archive, File(tempDir, "out-part"))

        assertTrue(File(result.destination, "Data/plugin.esp").isFile)
    }

    @Test
    fun extractZip_supportsNonstandardExtensionWithZipHeader() = runBlocking {
        val archive = File(tempDir, "mod.fomod")
        zip(archive, "Data/plugin.esp" to "plugin")

        val result = ModArchiveExtractor.extract(archive, File(tempDir, "out-fomod"))

        assertTrue(File(result.destination, "Data/plugin.esp").isFile)
    }

    @Test
    fun extractArchive_prefersSignatureOverMisleadingExtension() = runBlocking {
        val archive = File(tempDir, "download.rar")
        zip(archive, "Decktamer/game.exe" to "game")

        val result = ModArchiveExtractor.extract(archive, File(tempDir, "out-mislabeled"))

        assertEquals("zip", ModArchiveExtractor.archiveExtension(archive))
        assertTrue(File(result.destination, "Decktamer/game.exe").isFile)
    }

    @Test
    fun extractZip_supportsPasswordProtectedArchives() = runBlocking {
        val source = File(tempDir, "secret.txt").apply { writeText("decktamer payload") }
        val archive = File(tempDir, "protected.zip")
        val parameters = ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.ZIP_STANDARD
        }
        Zip4jFile(archive, "skidrowreloaded".toCharArray()).addFile(source, parameters)

        val result = ModArchiveExtractor.extract(
            archiveFile = archive,
            destination = File(tempDir, "out-protected"),
            password = "skidrowreloaded",
        )

        assertEquals("decktamer payload", File(result.destination, "secret.txt").readText())
    }

    @Test
    fun extractZip_rejectsMissingPasswordForProtectedArchives() = runBlocking {
        val source = File(tempDir, "secret.txt").apply { writeText("secret") }
        val archive = File(tempDir, "protected.zip")
        val parameters = ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.ZIP_STANDARD
        }
        Zip4jFile(archive, "skidrowreloaded".toCharArray()).addFile(source, parameters)

        val result = runCatching {
            ModArchiveExtractor.extract(archive, File(tempDir, "out-protected"))
        }

        assertTrue(result.exceptionOrNull() is ModArchivePasswordException)
        assertTrue(!File(tempDir, "out-protected").exists())
    }

    @Test
    fun extractExe_preservesExecutableAsPlaceableFile() = runBlocking {
        val archive = File(tempDir, "prefixed_installer.exe.part").apply {
            writeBytes(byteArrayOf(0x4D, 0x5A, 0, 0))
        }

        val result = ModArchiveExtractor.extract(
            archiveFile = archive,
            destination = File(tempDir, "out-exe"),
            preservedSingleFileName = "installer.exe",
        )

        assertTrue(File(result.destination, "installer.exe").isFile)
        assertEquals(listOf("installer.exe"), result.entries.map { it.path })
    }

    @Test
    fun unsafeArchivePath_detectsUnixWindowsAndDriveAbsolutePaths() {
        assertTrue(ModArchiveExtractor.isUnsafeArchivePath("/absolute/file.txt"))
        assertTrue(ModArchiveExtractor.isUnsafeArchivePath("\\absolute\\file.txt"))
        assertTrue(ModArchiveExtractor.isUnsafeArchivePath("C:\\absolute\\file.txt"))
        assertTrue(ModArchiveExtractor.isUnsafeArchivePath("C:/absolute/file.txt"))
    }

    @Test
    fun archiveRootEntry_acceptsIsoRootMarkerOnly() {
        assertTrue(ModArchiveExtractor.isArchiveRootEntry("."))
        assertTrue(ModArchiveExtractor.isArchiveRootEntry("./"))
        assertTrue(!ModArchiveExtractor.isArchiveRootEntry("../"))
        assertTrue(!ModArchiveExtractor.isArchiveRootEntry("/"))
        assertTrue(!ModArchiveExtractor.isArchiveRootEntry("./setup.exe"))
    }

    private fun zip(file: File, vararg entries: Pair<String, String>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
