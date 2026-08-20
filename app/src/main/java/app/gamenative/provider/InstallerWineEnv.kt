package app.gamenative.provider

import com.winlator.container.Container
import java.io.File

object InstallerWineEnv {
    const val EMULATOR = "Box64"

    fun isInstallerName(name: String): Boolean {
        val file = name.substringAfterLast('\\').substringAfterLast('/').lowercase()
        return file.contains("setup") || file.contains("install") || file.endsWith(".msi")
    }

    fun isInstaller(exeName: String, pack: File): Boolean {
        if (isInstallerName(exeName)) return true
        if (FitGirlPack.isPack(pack)) return true
        return pack.isDirectory &&
            ProviderLocalPayload.findInstaller(pack) != null &&
            ExecutableDiscovery.discover(pack).isEmpty()
    }

    fun packFolder(container: Container): File? {
        for (drive in Container.drivesIterator(container.drives)) {
            if (drive[0] == "A") return File(drive[1])
        }
        return null
    }

    fun apply(container: Container, pack: File): Boolean {
        var changed = false
        if (container.emulator != EMULATOR) {
            container.emulator = EMULATOR
            changed = true
        }
        if (!container.isWoW64Mode) {
            container.isWoW64Mode = true
            changed = true
        }
        if (FitGirlPack.isPack(pack)) {
            if (container.execArgs != FitGirlPack.EXEC_ARGS) {
                container.execArgs = FitGirlPack.EXEC_ARGS
                changed = true
            }
            val merged = FitGirlPack.mergeEnv(container.envVars)
            if (merged != container.envVars) {
                container.envVars = merged
                changed = true
            }
        }
        return changed
    }

    fun applyIfInstaller(container: Container, pack: File? = packFolder(container)): Boolean {
        val folder = pack ?: return false
        val exe = container.executablePath
        if (!isInstaller(exe, folder)) return false
        if (!apply(container, folder)) return false
        container.saveData()
        return true
    }
}
