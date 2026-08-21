package app.gamenative.provider

import java.io.File

object ExecutableDiscovery {
    private val excludedNames = listOf(
        "unins", "setup", "crash", "vcredist", "dxsetup", "redist",
        "unitycrash", "easyanticheat", "crashpad", "quicksfv", "sfv",
        "checksum", "hashcheck", "verify",
    )
    private val excludedDirs = listOf("md5", "_commonredist", "redist", "__redist")

    fun discover(destination: File): List<File> {
        if (!destination.exists()) return emptyList()
        return destination.walkTopDown()
            .filter { it.isFile && it.extension.equals("exe", ignoreCase = true) }
            .filter { file -> !isUtility(file, destination) }
            .toList()
    }

    fun pickLaunchExe(destination: File, titleHint: String = ""): File? {
        val found = discover(destination)
        if (found.isEmpty()) return null
        val hint = ProviderPathSlug.slug(titleHint.ifBlank { destination.name })
        return found.minWith(
            compareBy<File> { nameMismatch(it, hint) }
                .thenBy { depth(it, destination) }
                .thenBy { it.name.length },
        )
    }

    fun relativePath(root: File, file: File): String =
        file.relativeTo(root).invariantSeparatorsPath

    private fun nameMismatch(file: File, hint: String): Int {
        val slug = ProviderPathSlug.slug(file.nameWithoutExtension)
        if (hint.isBlank() || slug.isBlank()) return 1
        if (slug.contains(hint) || hint.contains(slug)) return 0
        val first = hint.substringBefore('-')
        return if (first.length >= 4 && slug.contains(first)) 0 else 1
    }

    private fun depth(file: File, root: File): Int =
        runCatching { file.relativeTo(root).invariantSeparatorsPath.count { it == '/' } }.getOrDefault(0)

    private fun isUtility(file: File, root: File): Boolean {
        val name = file.name.lowercase()
        if (excludedNames.any { name.contains(it) }) return true
        val parent = file.parentFile ?: return true
        val relative = runCatching { parent.relativeTo(root).path.lowercase() }.getOrDefault("")
        return excludedDirs.any { relative.contains(it) }
    }
}
