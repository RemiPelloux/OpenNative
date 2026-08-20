package app.gamenative.provider

import java.io.File

object InstallerCleanup {
    fun shouldSkip(job: TransferJob, installRoot: File): Boolean {
        val payload = File(job.finalPath)
        if (!payload.exists() || !installRoot.exists()) return payload.isDirectory
        val dest = runCatching { installRoot.canonicalFile }.getOrElse { return true }
        val file = runCatching { payload.canonicalFile }.getOrElse { return true }
        return file == dest || file.path.startsWith(dest.path + File.separator)
    }

    fun remove(job: TransferJob, installRoot: File, stagingRoot: File? = null) {
        if (shouldSkip(job, installRoot)) return
        deleteQuietly(File(job.finalPath))
        deleteQuietly(File(job.partialPath))
        val installerName = File(job.finalPath).name
        if (installerName.isNotBlank()) {
            deleteQuietly(File(installRoot, installerName))
        }
        val staging = File(job.destinationPath)
        if (isStaging(staging, installRoot, stagingRoot)) {
            staging.deleteRecursively()
        }
    }

    private fun isStaging(staging: File, installRoot: File, stagingRoot: File?): Boolean {
        if (!staging.exists()) return false
        if (staging.canonicalFile == installRoot.canonicalFile) return false
        if (stagingRoot == null) return false
        return staging.canonicalFile.path.startsWith(stagingRoot.canonicalFile.path + File.separator)
    }

    private fun deleteQuietly(file: File) {
        if (file.isFile) file.delete()
    }
}
