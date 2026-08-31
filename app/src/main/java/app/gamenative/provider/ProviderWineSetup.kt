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
    @Volatile private var pendingPack: String? = null
    @Volatile private var pendingPolicy: CleanupPolicy = CleanupPolicy.DELETE_AFTER_VERIFIED_INSTALL
    @Volatile private var hooked = false
    @Volatile private var session: InstallerSessionState? = null

    data class Launch(val appId: String, val pack: File)

    fun start(
        context: Context,
        dest: File,
        policy: CleanupPolicy = CleanupPolicy.DELETE_AFTER_VERIFIED_INSTALL,
    ): Launch? {
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
            InstallerWineEnv.apply(container, titleFolder)
            container.saveData()
        }
        pendingAppId = item.appId
        pendingPack = titleFolder.absolutePath
        pendingPolicy = policy
        session = InstallerSessionState(
            sessionId = item.appId,
            startedAtMs = System.currentTimeMillis(),
        )
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
        val pack = pendingPack?.let(::File) ?: return
        val policy = pendingPolicy
        val now = System.currentTimeMillis()
        val parent = InstallerSessionMachine.onParentExit(
            session ?: InstallerSessionState(appId, now),
            now,
        )
        val gameDir = InstallerGameDir.hostFolder(pack.name)
        val game = ExecutableDiscovery.discover(gameDir).firstOrNull()
            ?: ExecutableDiscovery.discover(pack).firstOrNull()
        val evaluated = InstallerSessionMachine.evaluate(
            state = parent,
            nowMs = now + InstallerSessionMachine.PARENT_GRACE_MS + InstallerSessionMachine.QUIESCENCE_MS,
            discoveredExe = game != null,
            rebootHint = false,
        )
        session = evaluated
        if (evaluated.outcome != InstallerSessionOutcome.QUIESCENT_SUCCESS || game == null) {
            return
        }
        pendingAppId = null
        pendingPack = null
        val root = if (game.absolutePath.startsWith(gameDir.absolutePath)) gameDir else pack
        runCatching {
            val container = ContainerUtils.getContainer(context, appId)
            InstallerGameDir.remapDriveA(container, root.absolutePath)
            container.executablePath = game.relativeTo(root).invariantSeparatorsPath
            container.saveData()
        }
        if (root == gameDir) {
            val marker = File(pack, ".gamenative")
            if (marker.exists()) marker.copyTo(File(gameDir, ".gamenative"), overwrite = false)
            val folders = PrefManager.customGameManualFolders.toMutableSet()
            folders.remove(pack.absolutePath)
            folders.add(gameDir.absolutePath)
            PrefManager.customGameManualFolders = folders
            CustomGameScanner.invalidateCache()
            if (policy == CleanupPolicy.DELETE_AFTER_VERIFIED_INSTALL) {
                InstallerCleanup.removePack(pack, gameDir)
            }
        }
    }
}
