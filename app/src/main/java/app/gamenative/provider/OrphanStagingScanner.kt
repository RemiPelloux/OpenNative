package app.gamenative.provider

import java.io.File

data class OrphanStaging(
    val path: String,
    val jobId: String,
    val ageMs: Long,
    val bytes: Long,
)

object OrphanStagingScanner {
    const val MIN_AGE_MS = 30 * 60 * 1000L

    fun scan(
        stagingRoot: File,
        activeJobIds: Set<String>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<OrphanStaging> {
        if (!stagingRoot.isDirectory) return emptyList()
        return stagingRoot.listFiles().orEmpty()
            .mapNotNull { file -> toOrphan(file, activeJobIds, nowMs) }
            .sortedByDescending { it.ageMs }
    }

    fun remove(orphans: List<OrphanStaging>) {
        orphans.forEach { orphan -> File(orphan.path).deleteRecursively() }
    }

    private fun toOrphan(file: File, activeJobIds: Set<String>, nowMs: Long): OrphanStaging? {
        val jobId = file.name.substringBefore("-extract").substringBefore('.')
        if (jobId in activeJobIds) return null
        val age = nowMs - file.lastModified()
        if (age < MIN_AGE_MS) return null
        return OrphanStaging(
            path = file.absolutePath,
            jobId = jobId,
            ageMs = age,
            bytes = sizeOf(file),
        )
    }

    private fun sizeOf(file: File): Long {
        if (file.isFile) return file.length()
        return file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
