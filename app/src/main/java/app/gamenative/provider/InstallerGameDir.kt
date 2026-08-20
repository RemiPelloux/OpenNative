package app.gamenative.provider

import android.os.Environment
import com.winlator.container.Container
import java.io.File

object InstallerGameDir {
    const val WINE_ROOT = "D:\\games"

    fun slug(title: String): String = ProviderPathSlug.slug(title)

    fun winePath(title: String): String = "$WINE_ROOT\\${slug(title)}"

    fun execArgs(title: String): String = "/DIR=\"${winePath(title)}\" /NORESTART"

    fun downloadsRoot(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    fun hostRoot(downloads: File = downloadsRoot()): File = File(downloads, "games")

    fun hostFolder(title: String, downloads: File = downloadsRoot()): File =
        File(hostRoot(downloads), slug(title))

    fun ensureHost(title: String, downloads: File = downloadsRoot()): File {
        val dir = hostFolder(title, downloads)
        dir.mkdirs()
        return dir
    }

    fun remapDriveA(container: Container, path: String) {
        val drives = StringBuilder("A:$path")
        for (drive in Container.drivesIterator(container.drives)) {
            if (drive[0] != "A") drives.append("${drive[0]}:${drive[1]}")
        }
        container.drives = drives.toString()
    }
}
