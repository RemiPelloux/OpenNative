package app.gamenative.provider

import java.io.File

object FitGirlPack {
    private val ARC = byteArrayOf(0x41, 0x72, 0x43, 0x01)
    const val INSTALL_DIR = InstallerGameDir.WINE_ROOT
    const val EXEC_ARGS = "/DIR=\"D:\\games\" /NORESTART"
    const val ENV =
        "WINEDLLOVERRIDES=isdone,unarc=n,b WINE_LARGE_ADDRESS_AWARE=0 TEMP=C:/windows/temp TMP=C:/windows/temp"

    fun isPack(folder: File): Boolean {
        if (!folder.isDirectory) return false
        val files = folder.walkTopDown().filter { it.isFile }.toList()
        val hasSetup = files.any { it.name.equals("setup.exe", ignoreCase = true) }
        return hasSetup && files.any { isArcBin(it) }
    }

    fun isArcBin(file: File): Boolean {
        if (!file.isFile || !file.name.startsWith("fg-", ignoreCase = true)) return false
        if (!file.extension.equals("bin", ignoreCase = true) || file.length() < 4L) return false
        val header = ByteArray(4)
        file.inputStream().use { stream ->
            if (stream.read(header) < 4) return false
        }
        return header.contentEquals(ARC)
    }

    fun mergeEnv(existing: String): String {
        if (existing.contains("WINEDLLOVERRIDES=isdone")) return existing
        return "$existing $ENV".trim()
    }
}
