package app.gamenative.container

import java.io.File

object DualSlot {
    const val SLOT_B = "slot-b"
    private val snapshotNames = listOf(
        PrefixMarker.FILE_NAME,
        "system.reg",
        "user.reg",
        ContainerControlStore.FILE_NAME,
    )

    fun slotDir(containerRoot: File): File = File(containerRoot, SLOT_B)

    fun snapshot(containerRoot: File, winePrefix: File): Boolean {
        val dest = slotDir(containerRoot)
        dest.mkdirs()
        var copied = 0
        for (name in snapshotNames) {
            val source = sourceFile(containerRoot, winePrefix, name)
            if (source == null || !source.isFile) continue
            source.copyTo(File(dest, name), overwrite = true)
            copied += 1
        }
        return copied > 0
    }

    fun flip(containerRoot: File, winePrefix: File): Boolean {
        val dest = slotDir(containerRoot)
        if (!dest.isDirectory) return false
        snapshotNames.forEach { name ->
            val backup = File(dest, name)
            if (!backup.isFile) return@forEach
            val target = sourceFile(containerRoot, winePrefix, name) ?: return@forEach
            target.parentFile?.mkdirs()
            backup.copyTo(target, overwrite = true)
        }
        return true
    }

    fun hasSlotB(containerRoot: File): Boolean =
        snapshotNames.any { File(slotDir(containerRoot), it).isFile }

    private fun sourceFile(containerRoot: File, winePrefix: File, name: String): File? = when (name) {
        PrefixMarker.FILE_NAME, ContainerControlStore.FILE_NAME -> File(containerRoot, name)
        "system.reg", "user.reg" -> File(winePrefix, name)
        else -> null
    }
}
