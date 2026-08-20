package app.gamenative.provider

import app.gamenative.utils.CustomGameScanner
import java.io.File

object ProviderLocalPayload {
    private val INSTALLER_EXT = setOf("exe", "msi", "rar", "zip", "7z", "bin")

    fun folder(item: ProviderFeedItem, root: File = File(CustomGameScanner.defaultRootPath)): File =
        ProviderPathSlug.resolveFolder(item.title, root)

    fun payloadFiles(item: ProviderFeedItem, root: File = File(CustomGameScanner.defaultRootPath)): List<File> {
        val dir = folder(item, root)
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".partial", ignoreCase = true) }
            .toList()
    }

    fun hasInstaller(item: ProviderFeedItem, root: File = File(CustomGameScanner.defaultRootPath)): Boolean =
        payloadFiles(item, root).any { it.extension.lowercase() in INSTALLER_EXT }

    fun findInstaller(folder: File): File? {
        if (!folder.isDirectory) return null
        val exes = folder.walkTopDown()
            .filter { it.isFile && it.extension.equals("exe", ignoreCase = true) }
            .toList()
        return exes.firstOrNull { exe ->
            val name = exe.name.lowercase()
            name.contains("setup") || name.contains("install")
        } ?: exes.firstOrNull()
    }

    fun hasGameExe(item: ProviderFeedItem, root: File = File(CustomGameScanner.defaultRootPath)): Boolean =
        ExecutableDiscovery.discover(folder(item, root)).isNotEmpty()

    fun roots(job: TransferJob?, item: ProviderFeedItem?, root: File = File(CustomGameScanner.defaultRootPath)): List<File> {
        val dirs = LinkedHashSet<File>()
        if (item != null) dirs.add(folder(item, root))
        job?.destinationPath?.takeIf { it.isNotBlank() }?.let { dirs.add(File(it)) }
        job?.finalPath?.takeIf { it.isNotBlank() }?.let { dirs.add(File(it)) }
        return dirs.toList()
    }

    fun resolve(job: TransferJob, item: ProviderFeedItem, root: File = File(CustomGameScanner.defaultRootPath)): File? {
        val dest = folder(item, root)
        return listOf(File(job.finalPath), dest)
            .filter { it.path.isNotBlank() }
            .distinctBy { it.absolutePath }
            .firstOrNull { hasPayload(it) }
    }

    fun relocatePack(pack: File): File {
        val safe = ProviderPathSlug.slug(pack.name)
        if (safe.isBlank() || safe == pack.name) return pack
        val target = File(pack.parentFile, safe)
        if (target.exists()) return target
        return if (pack.renameTo(target)) target else pack
    }

    fun hasPayload(file: File): Boolean {
        if (!file.exists()) return false
        if (file.isFile) {
            return file.length() > 0L && !file.name.endsWith(".partial", ignoreCase = true)
        }
        return file.walkTopDown().any { child ->
            child.isFile && !child.name.endsWith(".partial", ignoreCase = true)
        }
    }
}
