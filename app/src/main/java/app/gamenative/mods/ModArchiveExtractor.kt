package app.gamenative.mods

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import net.lingala.zip4j.exception.ZipException as Zip4jException
import net.lingala.zip4j.model.FileHeader
import timber.log.Timber

data class ModArchiveEntry(
    val path: String,
    val directory: Boolean,
    val sizeBytes: Long,
)

data class ModArchiveExtractionResult(
    val destination: File,
    val entries: List<ModArchiveEntry>,
)

data class ModArchiveExtractionProgress(
    val format: String,
    val entriesProcessed: Int,
    val totalEntries: Int,
    val extractedBytes: Long,
    val totalBytes: Long,
    val currentPath: String = "",
)

class UnsupportedModArchiveException(message: String) : IOException(message)
class ModArchivePasswordException(message: String, cause: Throwable? = null) : IOException(message, cause)

object ModArchiveExtractor {
    // Hard ceilings prevent archive bombs and bound memory, disk, and path handling costs.
    // Keep these aligned with LocalModImporter so switching source types cannot bypass them.
    private const val MAX_ENTRIES = ModImportSafetyLimits.MAX_ENTRIES
    private const val MAX_EXPANDED_BYTES = ModImportSafetyLimits.MAX_CONTENT_BYTES
    private const val MAX_RELATIVE_PATH_LENGTH = ModImportSafetyLimits.MAX_RELATIVE_PATH_LENGTH
    private const val MAX_PATH_SEGMENTS =
        ModImportSafetyLimits.MAX_DIRECTORY_DEPTH + 1 // Directory levels plus a file name.
    private const val ARCHIVE_READ_BLOCK_SIZE = 1024 * 1024
    private const val ISO_PRIMARY_VOLUME_DESCRIPTOR_OFFSET = 16L * 2048L
    private const val ZIP_EOCD_MIN_SIZE = 22
    private const val ZIP_MAX_COMMENT_SIZE = 65_535
    private val supportedArchiveExtensions = setOf("zip", "7z", "rar", "iso", "exe")

    @Volatile
    internal var testMaxEntries: Int? = null

    @Volatile
    internal var testMaxExpandedBytes: Long? = null

    private fun maxEntries(): Int = testMaxEntries ?: MAX_ENTRIES

    private fun maxExpandedBytes(): Long = testMaxExpandedBytes ?: MAX_EXPANDED_BYTES

    suspend fun extract(
        archiveFile: File,
        destination: File,
        preservedSingleFileName: String? = null,
        password: String = "",
        onProgress: (ModArchiveExtractionProgress) -> Unit = {},
    ): ModArchiveExtractionResult =
        withContext(Dispatchers.IO) {
            if (!archiveFile.isFile) throw IOException("Archive does not exist: ${archiveFile.absolutePath}")
            if (destination.exists() && !destination.deleteRecursively()) {
                throw IOException("Could not clear extraction directory: ${destination.absolutePath}")
            }
            if (!destination.mkdirs() && !destination.isDirectory) {
                throw IOException("Could not create extraction directory: ${destination.absolutePath}")
            }

            val archiveExtension = archiveExtension(archiveFile)
            val startedAt = System.nanoTime()
            val entries = try {
                when (archiveExtension) {
                    "zip" -> extractZip(archiveFile, destination, password, onProgress)
                    "7z" -> extractSevenZip(archiveFile, destination, password, onProgress)
                    "rar" -> extractRar(archiveFile, destination, password, onProgress)
                    "iso" -> extractIso(archiveFile, destination, password, onProgress)
                    "exe" -> preserveExecutableFile(archiveFile, destination, preservedSingleFileName, onProgress)
                    else -> throw UnsupportedModArchiveException("Unsupported archive type: .$archiveExtension")
                }
            } catch (e: Exception) {
                destination.deleteRecursively()
                throw e
            }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            Timber.i(
                "Extracted mod archive format=%s archiveBytes=%d extractedBytes=%d entries=%d elapsedMs=%d",
                archiveExtension,
                archiveFile.length(),
                entries.sumOf { it.sizeBytes },
                entries.size,
                elapsedMs,
            )
            ModArchiveExtractionResult(
                destination = destination,
                entries = entries.sortedBy { it.path.lowercase() },
            )
        }

    internal fun archiveExtension(archiveFile: File): String {
        // Keep .exe installers as single placeable files, but allow nonstandard Nexus
        // extensions to fall back to safe archive header detection.
        val extension = archiveFile.extension.lowercase()
        val inferred = if (extension == "part") {
            File(archiveFile.nameWithoutExtension).extension.lowercase()
        } else {
            extension
        }
        if (inferred == "exe") return inferred
        // Debrid hosts sometimes return a source RAR with a URL-derived .zip filename.
        // The verified payload signature must choose the decoder, not that filename.
        return archiveExtensionFromHeader(archiveFile).ifBlank { inferred }
    }

    private fun archiveExtensionFromHeader(archiveFile: File): String {
        val header = ByteArray(8)
        val read = archiveFile.inputStream().use { it.read(header) }
        if (read < 4) return ""
        return when {
            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() -> "zip"
            read >= 6 &&
                header[0] == 0x37.toByte() &&
                header[1] == 0x7A.toByte() &&
                header[2] == 0xBC.toByte() &&
                header[3] == 0xAF.toByte() &&
                header[4] == 0x27.toByte() &&
                header[5] == 0x1C.toByte() -> "7z"
            read >= 7 &&
                header[0] == 0x52.toByte() &&
                header[1] == 0x61.toByte() &&
                header[2] == 0x72.toByte() &&
                header[3] == 0x21.toByte() &&
                header[4] == 0x1A.toByte() &&
                header[5] == 0x07.toByte() -> "rar"
            hasIso9660Signature(archiveFile) -> "iso"
            else -> ""
        }
    }

    private fun hasIso9660Signature(archiveFile: File): Boolean {
        RandomAccessFile(archiveFile, "r").use { raf ->
            if (raf.length() < ISO_PRIMARY_VOLUME_DESCRIPTOR_OFFSET + 6L) return false
            raf.seek(ISO_PRIMARY_VOLUME_DESCRIPTOR_OFFSET + 1L)
            val identifier = ByteArray(5)
            raf.readFully(identifier)
            return identifier.contentEquals("CD001".toByteArray(Charsets.US_ASCII))
        }
    }

    fun listExtractedEntries(root: File): List<ModArchiveEntry> {
        if (!root.isDirectory) return emptyList()
        val base = root.canonicalFile
        return root.walkTopDown()
            .filter { it != root }
            .take(maxEntries())
            .mapNotNull { file ->
                val rel = file.canonicalFile.relativeToOrNull(base)?.invariantSeparatorsPath ?: return@mapNotNull null
                ModArchiveEntry(rel, file.isDirectory, if (file.isFile) file.length() else 0L)
            }
            .toList()
            .sortedBy { it.path.lowercase() }
    }

    private fun extractZip(
        archiveFile: File,
        destination: File,
        password: String,
        onProgress: (ModArchiveExtractionProgress) -> Unit,
    ): List<ModArchiveEntry> {
        val encryptedZip = net.lingala.zip4j.ZipFile(archiveFile)
        val isEncrypted = try {
            encryptedZip.isEncrypted
        } catch (error: Exception) {
            return recoverZipFromLocalHeaders(archiveFile, destination, password, onProgress, error)
        }
        if (isEncrypted) {
            if (password.isBlank()) {
                throw ModArchivePasswordException("This archive needs the password from the source post")
            }
            encryptedZip.setPassword(password.toCharArray())
            return try {
                extractEncryptedZip(encryptedZip, destination, onProgress)
            } catch (error: Exception) {
                if (error is ModArchivePasswordException || isProtectedArchiveFailure(error)) throw error
                recoverZipFromLocalHeaders(archiveFile, destination, password, onProgress, error)
            }
        }

        return try {
            extractIndexedZip(archiveFile, destination, onProgress)
        } catch (error: Exception) {
            if (isProtectedArchiveFailure(error)) throw error
            recoverZipFromLocalHeaders(archiveFile, destination, password, onProgress, error)
        }
    }

    private fun recoverZipFromLocalHeaders(
        archiveFile: File,
        destination: File,
        password: String,
        onProgress: (ModArchiveExtractionProgress) -> Unit,
        cause: Exception,
    ): List<ModArchiveEntry> {
        if (!hasZipEndOfCentralDirectory(archiveFile)) {
            throw IOException("ZIP download is incomplete or corrupt; download it again", cause)
        }
        Timber.w(cause, "Indexed ZIP extraction failed; retrying from local entry headers")
        resetExtractionDestination(destination)
        return try {
            extractStreamingZip(archiveFile, destination, password, onProgress)
        } catch (streamError: Exception) {
            if (streamError is ModArchivePasswordException || isProtectedArchiveFailure(streamError)) {
                throw streamError
            }
            Timber.w(streamError, "Local-header ZIP recovery failed; trying libarchive")
            resetExtractionDestination(destination)
            try {
                extractLibarchive(archiveFile, destination, "zip", password, onProgress) {
                    Archive.readSupportFormatZip(it)
                }
            } catch (fallbackError: Exception) {
                if (fallbackError is ModArchivePasswordException || isProtectedArchiveFailure(fallbackError)) {
                    throw fallbackError
                }
                if (fallbackError is LinkageError || fallbackError is UnsupportedModArchiveException) {
                    throw streamError
                }
                throw IOException("ZIP archive could not be recovered from local headers", fallbackError)
            }
        }
    }

    private fun isProtectedArchiveFailure(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("Unsafe archive path", ignoreCase = true) ||
            message.contains("escapes extraction", ignoreCase = true) ||
            message.contains("too many entries", ignoreCase = true) ||
            message.contains("expands beyond", ignoreCase = true)
    }

    private fun extractIndexedZip(
        archiveFile: File,
        destination: File,
        onProgress: (ModArchiveExtractionProgress) -> Unit,
    ): List<ModArchiveEntry> {
        val entries = mutableListOf<ModArchiveEntry>()
        var expandedBytes = 0L
        ZipFile(archiveFile).use { zip ->
            val totalEntries = zip.size()
            if (totalEntries > maxEntries()) throw IOException("Archive has too many entries")
            var totalBytes = 0L
            zip.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory && entry.size > 0L) {
                    totalBytes += entry.size
                    if (totalBytes > maxExpandedBytes()) {
                        throw IOException("Archive expands beyond the safety limit")
                    }
                }
            }
            zip.entries().asSequence().forEach { entry ->
                if (entries.size >= maxEntries()) throw IOException("Archive has too many entries")
                val outFile = safeDestination(destination, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                    entries += ModArchiveEntry(normalizeArchivePath(entry.name), true, 0L)
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).buffered(ARCHIVE_READ_BLOCK_SIZE).use { input ->
                        FileOutputStream(outFile).buffered(ARCHIVE_READ_BLOCK_SIZE).use { output ->
                            val buffer = ByteArray(ARCHIVE_READ_BLOCK_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                expandedBytes += read
                                if (expandedBytes > maxExpandedBytes()) {
                                    throw IOException("Archive expands beyond the safety limit")
                                }
                                output.write(buffer, 0, read)
                                emitProgress(onProgress, "zip", entries.size, totalEntries, expandedBytes, totalBytes, entry.name)
                            }
                        }
                    }
                    entries += ModArchiveEntry(normalizeArchivePath(entry.name), false, outFile.length())
                }
                emitProgress(onProgress, "zip", entries.size, totalEntries, expandedBytes, totalBytes, entry.name)
            }
        }
        return entries
    }

    private fun extractStreamingZip(
        archiveFile: File,
        destination: File,
        password: String,
        onProgress: (ModArchiveExtractionProgress) -> Unit,
    ): List<ModArchiveEntry> {
        val entries = mutableListOf<ModArchiveEntry>()
        var expandedBytes = 0L
        val passwordChars = password.takeIf { it.isNotBlank() }?.toCharArray()
        net.lingala.zip4j.io.inputstream.ZipInputStream(
            archiveFile.inputStream().buffered(ARCHIVE_READ_BLOCK_SIZE),
            passwordChars,
        ).use { zip ->
            while (true) {
                val header = try {
                    zip.nextEntry ?: break
                } catch (error: Zip4jException) {
                    if (password.isNotBlank()) {
                        throw ModArchivePasswordException(
                            "The archive password from the source post was rejected",
                            error,
                        )
                    }
                    throw error
                }
                val entryName = header.fileName
                if (header.isEncrypted && password.isBlank()) {
                    throw ModArchivePasswordException("This archive needs the password from the source post")
                }
                if (entries.size >= maxEntries()) throw IOException("Archive has too many entries")
                val outFile = safeDestination(destination, entryName)
                if (header.isDirectory) {
                    if (!outFile.mkdirs() && !outFile.isDirectory) {
                        throw IOException("Could not create archive directory: $entryName")
                    }
                    entries += ModArchiveEntry(normalizeArchivePath(entryName), true, 0L)
                } else {
                    val declaredSize = header.uncompressedSize
                    if (declaredSize > 0L && declaredSize > maxExpandedBytes() - expandedBytes) {
                        throw IOException("Archive expands beyond the safety limit")
                    }
                    outFile.parentFile?.let { parent ->
                        if (!parent.mkdirs() && !parent.isDirectory) {
                            throw IOException("Could not create archive directory: ${parent.absolutePath}")
                        }
                    }
                    FileOutputStream(outFile).buffered(ARCHIVE_READ_BLOCK_SIZE).use { output ->
                        val buffer = ByteArray(ARCHIVE_READ_BLOCK_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            if (read.toLong() > maxExpandedBytes() - expandedBytes) {
                                throw IOException("Archive expands beyond the safety limit")
                            }
                            expandedBytes += read
                            output.write(buffer, 0, read)
                            emitProgress(onProgress, "zip", entries.size, 0, expandedBytes, 0, entryName)
                        }
                    }
                    entries += ModArchiveEntry(normalizeArchivePath(entryName), false, outFile.length())
                }
                emitProgress(onProgress, "zip", entries.size, 0, expandedBytes, 0, entryName)
            }
        }
        if (entries.isEmpty()) throw IOException("ZIP archive contains no readable entries")
        return entries
    }

    private fun hasZipEndOfCentralDirectory(archiveFile: File): Boolean {
        RandomAccessFile(archiveFile, "r").use { raf ->
            val windowSize = minOf(raf.length(), (ZIP_EOCD_MIN_SIZE + ZIP_MAX_COMMENT_SIZE).toLong()).toInt()
            if (windowSize < ZIP_EOCD_MIN_SIZE) return false
            val tail = ByteArray(windowSize)
            raf.seek(raf.length() - windowSize)
            raf.readFully(tail)
            for (index in tail.size - ZIP_EOCD_MIN_SIZE downTo 0) {
                if (
                    tail[index] == 0x50.toByte() &&
                    tail[index + 1] == 0x4B.toByte() &&
                    tail[index + 2] == 0x05.toByte() &&
                    tail[index + 3] == 0x06.toByte()
                ) {
                    return true
                }
            }
            return false
        }
    }

    private fun resetExtractionDestination(destination: File) {
        if (destination.exists() && !destination.deleteRecursively()) {
            throw IOException("Could not clear extraction directory: ${destination.absolutePath}")
        }
        if (!destination.mkdirs() && !destination.isDirectory) {
            throw IOException("Could not create extraction directory: ${destination.absolutePath}")
        }
    }

    private fun extractEncryptedZip(
        zip: net.lingala.zip4j.ZipFile,
        destination: File,
        onProgress: (ModArchiveExtractionProgress) -> Unit,
    ): List<ModArchiveEntry> {
        val headers = try {
            zip.fileHeaders
        } catch (error: Zip4jException) {
            throw IOException("ZIP archive could not be read", error)
        }
        if (headers.size > maxEntries()) throw IOException("Archive has too many entries")

        var totalBytes = 0L
        headers.forEach { header ->
            safeDestination(destination, header.fileName)
            val size = header.uncompressedSize.coerceAtLeast(0L)
            if (!header.isDirectory && size > maxExpandedBytes() - totalBytes) {
                throw IOException("Archive expands beyond the safety limit")
            }
            totalBytes += size
        }

        val entries = mutableListOf<ModArchiveEntry>()
        var expandedBytes = 0L
        try {
            headers.forEach { header ->
                val outFile = safeDestination(destination, header.fileName)
                if (header.isDirectory) {
                    if (!outFile.mkdirs() && !outFile.isDirectory) {
                        throw IOException("Could not create archive directory: ${header.fileName}")
                    }
                    entries += ModArchiveEntry(normalizeArchivePath(header.fileName), true, 0L)
                } else {
                    outFile.parentFile?.let { parent ->
                        if (!parent.mkdirs() && !parent.isDirectory) {
                            throw IOException("Could not create archive directory: ${parent.absolutePath}")
                        }
                    }
                    expandedBytes = copyEncryptedZipEntry(
                        zip = zip,
                        header = header,
                        outFile = outFile,
                        alreadyExpandedBytes = expandedBytes,
                        totalEntries = headers.size,
                        totalBytes = totalBytes,
                        entriesProcessed = entries.size,
                        onProgress = onProgress,
                    )
                    entries += ModArchiveEntry(normalizeArchivePath(header.fileName), false, outFile.length())
                }
                emitProgress(
                    onProgress,
                    "zip",
                    entries.size,
                    headers.size,
                    expandedBytes,
                    totalBytes,
                    header.fileName,
                )
            }
        } catch (error: Zip4jException) {
            val message = error.message.orEmpty()
            if (message.contains("password", ignoreCase = true)) {
                throw ModArchivePasswordException("The archive password from the source post was rejected", error)
            }
            throw IOException("ZIP archive could not be read", error)
        }
        return entries
    }

    private fun copyEncryptedZipEntry(
        zip: net.lingala.zip4j.ZipFile,
        header: FileHeader,
        outFile: File,
        alreadyExpandedBytes: Long,
        totalEntries: Int,
        totalBytes: Long,
        entriesProcessed: Int,
        onProgress: (ModArchiveExtractionProgress) -> Unit,
    ): Long {
        var expandedBytes = alreadyExpandedBytes
        zip.getInputStream(header).buffered(ARCHIVE_READ_BLOCK_SIZE).use { input ->
            FileOutputStream(outFile).buffered(ARCHIVE_READ_BLOCK_SIZE).use { output ->
                val buffer = ByteArray(ARCHIVE_READ_BLOCK_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    if (read.toLong() > maxExpandedBytes() - expandedBytes) {
                        throw IOException("Archive expands beyond the safety limit")
                    }
                    expandedBytes += read
                    output.write(buffer, 0, read)
                    emitProgress(
                        onProgress,
                        "zip",
                        entriesProcessed,
                        totalEntries,
                        expandedBytes,
                        totalBytes,
                        header.fileName,
                    )
                }
            }
        }
        return expandedBytes
    }

    private fun extractSevenZip(
        archiveFile: File,
        destination: File,
        password: String,
        onProgress: (ModArchiveExtractionProgress) -> Unit,
    ): List<ModArchiveEntry> =
        extractLibarchive(archiveFile, destination, "7z", password, onProgress) { Archive.readSupportFormat7zip(it) }

    private fun extractRar(
        archiveFile: File,
        destination: File,
        password: String,
        onProgress: (ModArchiveExtractionProgress) -> Unit,
    ): List<ModArchiveEntry> =
        extractLibarchive(archiveFile, destination, "rar", password, onProgress) {
            Archive.readSupportFormatRar(it)
            Archive.readSupportFormatRar5(it)
        }

    private fun extractIso(
        archiveFile: File,
        destination: File,
        password: String,
        onProgress: (ModArchiveExtractionProgress) -> Unit,
    ): List<ModArchiveEntry> =
        extractLibarchive(archiveFile, destination, "iso", password, onProgress) {
            Archive.readSupportFormatIso9660(it)
        }

    private fun preserveExecutableFile(
        archiveFile: File,
        destination: File,
        preservedFileName: String?,
        onProgress: (ModArchiveExtractionProgress) -> Unit,
    ): List<ModArchiveEntry> {
        val fileName = sanitizePreservedFileName(preservedFileName) ?: archiveFile.name.removeSuffix(".part")
        val outFile = safeDestination(destination, fileName)
        outFile.parentFile?.mkdirs()
        var copiedBytes = 0L
        archiveFile.inputStream().buffered(ARCHIVE_READ_BLOCK_SIZE).use { input ->
            FileOutputStream(outFile).buffered(ARCHIVE_READ_BLOCK_SIZE).use { output ->
                val buffer = ByteArray(ARCHIVE_READ_BLOCK_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    copiedBytes += read
                    if (copiedBytes > maxExpandedBytes()) {
                        throw IOException("Archive expands beyond the safety limit")
                    }
                    output.write(buffer, 0, read)
                    emitProgress(onProgress, "exe", 0, 1, copiedBytes, archiveFile.length(), fileName)
                }
            }
        }
        emitProgress(onProgress, "exe", 1, 1, copiedBytes, archiveFile.length(), fileName)
        return listOf(ModArchiveEntry(normalizeArchivePath(fileName), directory = false, sizeBytes = outFile.length()))
    }

    private fun extractLibarchive(
        archiveFile: File,
        destination: File,
        format: String,
        password: String,
        onProgress: (ModArchiveExtractionProgress) -> Unit,
        supportFormat: (Long) -> Unit,
    ): List<ModArchiveEntry> {
        val entries = mutableListOf<ModArchiveEntry>()
        var expandedBytes = 0L
        val archive = try {
            Archive.readNew()
        } catch (e: LinkageError) {
            throw UnsupportedModArchiveException("${format.uppercase()} extraction is not available on this device")
        }

        try {
            Archive.setCharset(archive, Charsets.UTF_8.name().toByteArray(Charsets.UTF_8))
            Archive.readSupportFilterAll(archive)
            supportFormat(archive)
            if (password.isNotBlank()) {
                Archive.readAddPassphrase(archive, password.toByteArray(Charsets.UTF_8))
            }
            Archive.readOpenFileName(
                archive,
                archiveFile.absolutePath.toByteArray(Charsets.UTF_8),
                ARCHIVE_READ_BLOCK_SIZE.toLong(),
            )

            while (true) {
                val entry = try {
                    Archive.readNextHeader(archive)
                } catch (e: ArchiveException) {
                    if (e.code == Archive.ERRNO_EOF) break else throw e
                }
                if (entry == 0L) break
                if (entries.size >= maxEntries()) throw IOException("Archive has too many entries")
                if (ArchiveEntry.isEncrypted(entry) && password.isBlank()) {
                    throw ModArchivePasswordException("This archive needs the password from the source post")
                }

                val entryName = archiveEntryName(entry)
                val normalized = normalizeArchivePath(entryName)
                val entryType = ArchiveEntry.filetype(entry)
                // ISO9660 readers expose the volume root as a synthetic "." directory.
                // It has no payload or destination of its own, so ignore only that exact root marker.
                if (entryType == ArchiveEntry.AE_IFDIR && isArchiveRootEntry(entryName)) continue

                val outFile = safeDestination(destination, entryName)
                when (entryType) {
                    ArchiveEntry.AE_IFDIR -> {
                        outFile.mkdirs()
                        entries += ModArchiveEntry(normalized, true, 0L)
                    }
                    ArchiveEntry.AE_IFREG, 0 -> {
                        outFile.parentFile?.mkdirs()
                        val written = FileOutputStream(outFile).use { output ->
                            copyLibarchiveEntryData(archive, output, expandedBytes) { currentExpandedBytes ->
                                emitProgress(onProgress, format, entries.size, 0, currentExpandedBytes, 0, normalized)
                            }
                        }
                        expandedBytes += written
                        entries += ModArchiveEntry(normalized, false, outFile.length())
                    }
                    ArchiveEntry.AE_IFLNK -> throw UnsupportedModArchiveException(
                        "${format.uppercase()} archives with symlinks are not supported",
                    )
                    else -> throw UnsupportedModArchiveException(
                        "${format.uppercase()} archive contains an unsupported entry type: $entryName",
                    )
                }
                emitProgress(onProgress, format, entries.size, 0, expandedBytes, 0, normalized)
            }
        } catch (e: ArchiveException) {
            throw friendlyLibarchiveException(format, e)
        } finally {
            runCatching { Archive.readClose(archive) }
            runCatching { Archive.readFree(archive) }
        }
        return entries
    }

    private fun emitProgress(
        onProgress: (ModArchiveExtractionProgress) -> Unit,
        format: String,
        entriesProcessed: Int,
        totalEntries: Int,
        extractedBytes: Long,
        totalBytes: Long,
        currentPath: String,
    ) {
        onProgress(
            ModArchiveExtractionProgress(
                format = format,
                entriesProcessed = entriesProcessed,
                totalEntries = totalEntries,
                extractedBytes = extractedBytes,
                totalBytes = totalBytes,
                currentPath = normalizeArchivePath(currentPath),
            ),
        )
    }

    private fun copyLibarchiveEntryData(
        archive: Long,
        output: FileOutputStream,
        alreadyExpandedBytes: Long,
        onBytesCopied: (Long) -> Unit,
    ): Long {
        val buffer = ByteBuffer.allocateDirect(ARCHIVE_READ_BLOCK_SIZE)
        var written = 0L
        while (true) {
            buffer.clear()
            try {
                Archive.readData(archive, buffer)
            } catch (e: ArchiveException) {
                if (e.code == Archive.ERRNO_EOF) break else throw e
            }
            val read = buffer.position()
            if (read <= 0) break
            written += read.toLong()
            if (alreadyExpandedBytes + written > maxExpandedBytes()) {
                throw IOException("Archive expands beyond the safety limit")
            }
            onBytesCopied(alreadyExpandedBytes + written)
            buffer.flip()
            while (buffer.hasRemaining()) {
                output.channel.write(buffer)
            }
        }
        return written
    }

    private fun archiveEntryName(entry: Long): String {
        val utf8Name = ArchiveEntry.pathnameUtf8(entry)
        if (!utf8Name.isNullOrBlank()) return utf8Name
        val rawName = ArchiveEntry.pathname(entry)
        if (rawName != null && rawName.isNotEmpty()) return rawName.toString(Charsets.UTF_8)
        throw IOException("Archive contains an entry without a path")
    }

    private fun friendlyLibarchiveException(format: String, error: ArchiveException): IOException {
        val message = error.message.orEmpty()
        val label = format.uppercase()
        return when {
            message.contains("encrypted", ignoreCase = true) ->
                ModArchivePasswordException("The archive password from the source post was rejected", error)
            message.contains("passphrase", ignoreCase = true) ||
                message.contains("password", ignoreCase = true) ->
                ModArchivePasswordException("The archive password from the source post was rejected", error)
            message.contains("multi-volume", ignoreCase = true) ||
                message.contains("multi volume", ignoreCase = true) ||
                message.contains("volume", ignoreCase = true) ->
                UnsupportedModArchiveException("Multipart $label archives are not supported yet")
            else -> IOException("$label extraction failed: ${message.ifBlank { "archive could not be read" }}", error)
        }
    }

    private fun safeDestination(destination: File, rawName: String): File {
        if (
            rawName.length > MAX_RELATIVE_PATH_LENGTH ||
            isUnsafeArchivePath(rawName)
        ) {
            throw IOException("Unsafe archive path: $rawName")
        }
        val normalized = normalizeArchivePath(rawName)
        val segments = normalized.split('/')
        if (
            normalized.isBlank() ||
            normalized.length > MAX_RELATIVE_PATH_LENGTH ||
            segments.size > MAX_PATH_SEGMENTS ||
            segments.any { it == ".." }
        ) {
            throw IOException("Unsafe archive path: $rawName")
        }
        val outFile = File(destination, normalized).canonicalFile
        val destCanonical = destination.canonicalFile
        if (!outFile.path.startsWith(destCanonical.path + File.separator) && outFile != destCanonical) {
            throw IOException("Archive entry escapes extraction directory: $rawName")
        }
        return outFile
    }

    internal fun isUnsafeArchivePath(path: String): Boolean {
        val trimmed = path.trim()
        return trimmed.startsWith("/") ||
            trimmed.startsWith("\\") ||
            Regex("^[A-Za-z]:.*").containsMatchIn(trimmed)
    }

    internal fun isArchiveRootEntry(path: String): Boolean =
        path.trim().replace('\\', '/').trimEnd('/') == "."

    private fun normalizeArchivePath(path: String): String =
        path.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
            .joinToString("/")

    private fun sanitizePreservedFileName(name: String?): String? =
        name
            ?.replace('\\', '/')
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() && !isUnsafeArchivePath(it) }
}
