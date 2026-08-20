package app.gamenative.provider

import android.content.Context
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.events.AndroidEvent
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.CustomGameScanner
import java.io.File

object ProviderWineSetup {
    @Volatile private var pendingAppId: String? = null
    @Volatile private var pendingDest: String? = null
    @Volatile private var hooked = false

    data class Launch(val appId: String, val pack: File)

    fun start(context: Context, dest: File): Launch? {
        hook(context.applicationContext)
        val titleFolder = ProviderLocalPayload.flattenInstaller(
            ProviderLocalPayload.migrateOffFuse(ProviderLocalPayload.relocatePack(dest)),
        )
        val launchExe = ProviderLocalPayload.findInstaller(titleFolder)
        val folders = PrefManager.customGameManualFolders.toMutableSet()
        folders.removeAll { !File(it).isDirectory }
        folders.add(titleFolder.absolutePath)
        PrefManager.customGameManualFolders = folders
        CustomGameScanner.invalidateCache()
        val item = CustomGameScanner.createLibraryItemFromFolder(titleFolder.absolutePath) ?: return null
        if (launchExe != null) {
            val container = ContainerUtils.getOrCreateContainer(context, item.appId)
            container.executablePath = launchExe.name
            container.emulator = InstallerWineEnv.EMULATOR
            container.isWoW64Mode = true
            if (FitGirlPack.isPack(titleFolder)) {
                container.execArgs = FitGirlPack.EXEC_ARGS
                container.envVars = FitGirlPack.mergeEnv(container.envVars)
            }
            container.saveData()
        }
        pendingAppId = item.appId
        pendingDest = titleFolder.absolutePath
        return Launch(item.appId, titleFolder)
    }

    private fun hook(appContext: Context) {
        if (hooked) return
        hooked = true
        PluviaApp.events.on<AndroidEvent.GuestProgramTerminated, Unit> {
            promote(appContext)
        }
    }

    private fun promote(context: Context) {
        val appId = pendingAppId ?: return
        val dest = pendingDest?.let(::File) ?: return
        pendingAppId = null
        pendingDest = null
        val game = ExecutableDiscovery.discover(dest).firstOrNull() ?: return
        runCatching {
            val container = ContainerUtils.getContainer(context, appId)
            container.executablePath = game.relativeTo(dest).invariantSeparatorsPath
            container.saveData()
        }
    }
}
