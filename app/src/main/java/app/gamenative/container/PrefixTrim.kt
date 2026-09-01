package app.gamenative.container

import java.io.File

data class PrefixTrimCandidate(
    val relativePath: String,
    val bytes: Long,
    val kind: String,
)

data class PrefixTrimPreview(
    val candidates: List<PrefixTrimCandidate>,
    val reclaimBytes: Long,
) {
    companion object {
        val EMPTY = PrefixTrimPreview(emptyList(), 0L)
    }
}

object PrefixTrim {
    private val reclaimKinds = listOf(
        "drive_c/windows/temp" to "temp",
        "drive_c/users/xuser/Temp" to "temp",
        "drive_c/windows/Minidump" to "crash",
        "drive_c/windows/memory.dmp" to "crash",
        "drive_c/users/xuser/AppData/Local/CrashDumps" to "crash",
        "drive_c/users/xuser/AppData/Local/Temp" to "temp",
        "drive_c/windows/Installer/\$PatchCache\$" to "installer",
    )

    private val sacred = listOf(
        "saved games",
        "userdata",
        "steamapps",
        "drive_c/users/xuser/documents",
    )

    fun preview(winePrefix: File): PrefixTrimPreview {
        if (!winePrefix.isDirectory) return PrefixTrimPreview.EMPTY
        val found = reclaimKinds.mapNotNull { (relative, kind) ->
            val file = File(winePrefix, relative)
            if (!file.exists() || isSacred(relative)) return@mapNotNull null
            PrefixTrimCandidate(relative, sizeOf(file), kind)
        }
        return PrefixTrimPreview(found, found.sumOf { it.bytes })
    }

    fun apply(winePrefix: File, preview: PrefixTrimPreview): Long {
        var deleted = 0L
        for (candidate in preview.candidates) {
            if (isSacred(candidate.relativePath)) continue
            val file = File(winePrefix, candidate.relativePath)
            if (!file.exists()) continue
            deleted += sizeOf(file)
            file.deleteRecursively()
        }
        return deleted
    }

    fun isSacred(relativePath: String): Boolean {
        val lower = relativePath.lowercase()
        return sacred.any { lower.contains(it) }
    }

    private fun sizeOf(file: File): Long {
        if (file.isFile) return file.length()
        return file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
