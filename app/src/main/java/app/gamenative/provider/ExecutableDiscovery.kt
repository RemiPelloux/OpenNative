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

    private fun isUtility(file: File, root: File): Boolean {
        val name = file.name.lowercase()
        if (excludedNames.any { name.contains(it) }) return true
        val parent = file.parentFile ?: return true
        val relative = runCatching { parent.relativeTo(root).path.lowercase() }.getOrDefault("")
        return excludedDirs.any { relative.contains(it) }
    }
}
