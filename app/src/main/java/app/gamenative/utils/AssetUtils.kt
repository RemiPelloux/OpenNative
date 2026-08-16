package app.gamenative.utils

import android.content.res.AssetManager
import app.gamenative.performance.runtime.ComponentInstallPolicy
import com.winlator.core.TarCompressorUtils
import timber.log.Timber
import java.io.File

object AssetUtils {
    data class VersionedComponent(
        val assetFile: String,
        val targetDir: File,
        val fingerprint: String,
        val criticalRelativePaths: List<String>,
    )

    fun log() : Timber.Tree {
        return Timber.tag("AssetUtils")
    }

    /**
     * Extracts a component only when its version changes or critical files are damaged.
     *
     * @param components Versioned assets, destinations and integrity sentinels
     * @param assetManager AssetManager to access asset files
     * @param extractType Compression type (ZSTD or XZ)
     */
    fun extractComponentsWithVersionCheck(
        components: List<VersionedComponent>,
        assetManager: AssetManager,
        extractType: TarCompressorUtils.Type
    ) {
        for (component in components) {
            val assetFile = component.assetFile
            val targetDir = component.targetDir
            val marker = File(targetDir, ".opennative-component")
            val criticalFiles = component.criticalRelativePaths.map { File(targetDir, it) }
            if (!ComponentInstallPolicy.needsInstall(marker, component.fingerprint, criticalFiles)) {
                log().d("Component $assetFile is current and healthy")
                continue
            }
            log().i("Extracting $assetFile to ${targetDir.absolutePath}")
            val tempDir = File(targetDir.parentFile, "${targetDir.name}.tmp")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            val success = TarCompressorUtils.extract(
                extractType,
                assetManager,
                assetFile,
                tempDir
            )

            if (success) {
                val extractedCriticalFiles = component.criticalRelativePaths.map { File(tempDir, it) }
                if (!ComponentInstallPolicy.isHealthy(extractedCriticalFiles)) {
                    tempDir.deleteRecursively()
                    log().e("Rejected incomplete component $assetFile")
                    continue
                }
                val backupDir = File(targetDir.parentFile, "${targetDir.name}.backup")
                if (backupDir.exists()) backupDir.deleteRecursively()
                if (targetDir.exists() && !targetDir.renameTo(backupDir)) {
                    tempDir.deleteRecursively()
                    log().e("Failed to stage existing component for $assetFile")
                    continue
                }
                if (!tempDir.renameTo(targetDir)) {
                    log().e("Failed to promote extracted dir for $assetFile")
                    tempDir.deleteRecursively()
                    if (backupDir.exists()) backupDir.renameTo(targetDir)
                    continue
                }
                backupDir.deleteRecursively()
                if (!ComponentInstallPolicy.markInstalled(marker, component.fingerprint)) {
                    log().w("Installed $assetFile but could not persist its version marker")
                }
                log().i("Successfully extracted $assetFile")
            } else {
                tempDir.deleteRecursively()
                log().e("Failed to extract $assetFile")
            }
        }
    }
}
