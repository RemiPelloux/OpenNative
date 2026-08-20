package app.gamenative.provider

import java.io.File

object ExecutableDiscovery {
    private val excluded = listOf(
        "unins", "setup", "crash", "vcredist", "dxsetup", "redist",
        "unitycrash", "easyanticheat", "crashpad",
    )

    fun discover(destination: File): List<File> {
        if (!destination.exists()) return emptyList()
        return destination.walkTopDown()
            .filter { it.isFile && it.extension.equals("exe", ignoreCase = true) }
            .filter { file -> excluded.none { file.name.lowercase().contains(it) } }
            .toList()
    }
}
