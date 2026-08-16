package app.gamenative.performance.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Cheap launch-time integrity gate for managed runtime components.
 * A matching marker is accepted only while every critical file still exists
 * and is non-empty. Extraction writes the marker after a successful install.
 */
object ComponentInstallPolicy {
    private const val MARKER_VERSION = "v1"

    fun needsInstall(marker: File, fingerprint: String, criticalFiles: Collection<File>): Boolean {
        if (fingerprint.isBlank() || criticalFiles.isEmpty()) return true
        if (marker.readMarkerOrNull() != markerValue(fingerprint)) return true
        return !isHealthy(criticalFiles)
    }

    fun isHealthy(criticalFiles: Collection<File>): Boolean =
        criticalFiles.isNotEmpty() && criticalFiles.all { file -> file.isFile && file.length() > 0L }

    fun markInstalled(marker: File, fingerprint: String): Boolean {
        if (fingerprint.isBlank()) return false
        marker.parentFile?.let { parent ->
            if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) return false
        }
        val parent = marker.parentFile ?: return false
        val temp = File(parent, "${marker.name}.tmp")
        return runCatching {
            temp.writeText(markerValue(fingerprint), Charsets.UTF_8)
            runCatching {
                Files.move(
                    temp.toPath(),
                    marker.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.recoverCatching {
                Files.move(temp.toPath(), marker.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }.getOrThrow()
            true
        }.getOrElse {
            temp.delete()
            false
        }
    }

    fun fingerprint(vararg values: String?): String = values.joinToString(":") { value ->
        value.orEmpty().trim().replace(":", "::")
    }

    private fun File.readMarkerOrNull(): String? = runCatching {
        takeIf { it.isFile && length() in 1..4_096 }?.readText(Charsets.UTF_8)?.trim()
    }.getOrNull()

    private fun markerValue(fingerprint: String): String = "$MARKER_VERSION:$fingerprint"
}
