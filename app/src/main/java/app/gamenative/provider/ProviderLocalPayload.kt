package app.gamenative.provider

import app.gamenative.utils.CustomGameScanner
import java.io.File

object ProviderLocalPayload {
    private val INSTALLER_EXT = setOf("exe", "msi", "rar", "zip", "7z", "bin")

    fun writeRoot(): File = File(CustomGameScanner.importRootPath)

    fun folder(item: ProviderFeedItem, root: File? = null): File {
        if (root != null) return ProviderPathSlug.resolveFolder(item.title, root)
        val candidates = runCatching {
            listOf(writeRoot(), File(CustomGameScanner.defaultRootPath)).distinctBy { it.absolutePath }
        }.getOrDefault(emptyList())
        for (candidate in candidates) {
            val found = ProviderPathSlug.resolveFolder(item.title, candidate)
            if (found.isDirectory) return found
        }
        val fallback = candidates.firstOrNull() ?: File(".")
        return ProviderPathSlug.resolveFolder(item.title, fallback)
    }

    fun payloadFiles(item: ProviderFeedItem, root: File? = null): List<File> {
        val dir = folder(item, root)
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".partial", ignoreCase = true) }
            .toList()
    }

    fun hasInstaller(item: ProviderFeedItem, root: File? = null): Boolean =
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

    fun hasGameExe(item: ProviderFeedItem, root: File? = null): Boolean =
        ExecutableDiscovery.discover(folder(item, root)).isNotEmpty()

    fun roots(job: TransferJob?, item: ProviderFeedItem?, root: File? = null): List<File> {
        val dirs = LinkedHashSet<File>()
        if (item != null) dirs.add(folder(item, root))
        job?.destinationPath?.takeIf { it.isNotBlank() }?.let { dirs.add(File(it)) }
        job?.finalPath?.takeIf { it.isNotBlank() }?.let { dirs.add(File(it)) }
        return dirs.toList()
    }

    fun resolve(job: TransferJob, item: ProviderFeedItem, root: File? = null): File? {
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

    fun flattenInstaller(root: File): File {
        val installer = findInstaller(root) ?: return root
        val pack = installer.parentFile ?: return root
        if (pack.absolutePath == root.absolutePath) return root
        pack.listFiles()?.forEach { child ->
            if (child.name == ".gamenative") return@forEach
            val target = File(root, child.name)
            if (!target.exists()) child.renameTo(target)
        }
        File(pack, ".gamenative").delete()
        pack.deleteRecursively()
        return root
    }

    fun migrateOffFuse(folder: File, publicRoot: File = writeRoot()): File {
        if (!folder.exists() || !folder.absolutePath.contains("/Android/data/")) return folder
        if (!publicRoot.isDirectory && !publicRoot.mkdirs()) return folder
        val target = File(publicRoot, folder.name)
        if (target.exists()) return target
        return if (folder.renameTo(target)) target else folder
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
