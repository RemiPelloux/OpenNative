package app.gamenative.provider

import java.io.File
import java.util.zip.ZipFile

object ArchiveInspector {
    const val MAX_ENTRIES = 20_000
    const val MAX_RATIO = 100L

    fun inspectZip(archive: File) {
        ZipFile(archive).use { zip ->
            var entries = 0
            var uncompressed = 0L
            val names = zip.entries()
            while (names.hasMoreElements()) {
                val entry = names.nextElement()
                entries++
                if (entries > MAX_ENTRIES) {
                    throw ProviderException(ProviderErrorCode.PATH_ESCAPE, "Archive has too many entries")
                }
                val name = entry.name.replace('\\', '/')
                if (name.startsWith("/") || name.contains("..")) {
                    throw ProviderException(ProviderErrorCode.PATH_ESCAPE, "Archive path is unsafe")
                }
                uncompressed += entry.size.coerceAtLeast(0L)
            }
            val compressed = archive.length().coerceAtLeast(1L)
            if (uncompressed / compressed > MAX_RATIO) {
                throw ProviderException(ProviderErrorCode.PATH_ESCAPE, "Archive compression ratio is unsafe")
            }
        }
    }

    fun confinedPath(stagingDir: File, relative: String): File {
        val normalized = relative.replace('\\', '/').trimStart('/')
        if (normalized.contains("..") || normalized.startsWith("/")) {
            throw ProviderException(ProviderErrorCode.PATH_ESCAPE, "Extract path is unsafe")
        }
        val target = File(stagingDir, normalized).canonicalFile
        val root = stagingDir.canonicalFile
        if (!target.path.startsWith(root.path + File.separator) && target != root) {
            throw ProviderException(ProviderErrorCode.PATH_ESCAPE, "Extract path escaped staging")
        }
        return target
    }
}
