package app.gamenative.utils

import android.content.Context
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import java.io.File
import org.json.JSONObject
import timber.log.Timber

/**
 * One Wine prefix shared by many games, like GameHub / Winlator.
 * Existing dedicated containers stay isolated. New games join the shared prefix
 * when [app.gamenative.PrefManager.useSharedWinePrefix] is on.
 */
object SharedWinePrefix {
    const val CONTAINER_ID = "SHARED_PREFIX"
    const val EXTRA_BOUND_APP_ID = "boundAppId"
    private const val OVERLAY_DIR = "shared-prefix-overlays"
    private const val KEY_EXECUTABLE = "executablePath"

    fun isSharedId(id: String): Boolean = id == CONTAINER_ID

    fun resolveContainerId(
        appId: String,
        hasDedicated: Boolean,
        sharedEnabled: Boolean,
    ): String {
        if (isSharedId(appId)) return CONTAINER_ID
        if (hasDedicated) return appId
        return if (sharedEnabled) CONTAINER_ID else appId
    }

    fun resolveContainerId(
        manager: ContainerManager,
        appId: String,
        sharedEnabled: Boolean,
    ): String = resolveContainerId(appId, manager.hasContainer(appId), sharedEnabled)

    fun hasMembership(context: Context, appId: String): Boolean = overlayFile(context, appId).exists()

    fun rememberMember(context: Context, appId: String, executablePath: String = "") {
        if (appId.isBlank() || isSharedId(appId)) return
        writeOverlay(context, appId, executablePath.ifBlank { loadExecutable(context, appId) })
    }

    fun loadExecutable(context: Context, appId: String): String {
        val file = overlayFile(context, appId)
        if (!file.exists()) return ""
        return runCatching {
            JSONObject(file.readText()).optString(KEY_EXECUTABLE, "")
        }.getOrDefault("")
    }

    fun saveExecutable(context: Context, appId: String, executablePath: String) {
        if (appId.isBlank() || isSharedId(appId)) return
        writeOverlay(context, appId, executablePath)
    }

    fun release(context: Context, appId: String) {
        if (appId.isBlank() || isSharedId(appId)) return
        val file = overlayFile(context, appId)
        if (file.exists() && !file.delete()) {
            Timber.w("Could not remove shared-prefix overlay for %s", appId)
        }
    }

    fun memberCount(context: Context): Int =
        overlayDir(context).listFiles()?.count { it.isFile && it.extension == "json" } ?: 0

    fun bindLaunch(context: Context, container: Container, appId: String) {
        if (!isSharedId(container.id) || appId.isBlank()) return
        container.putExtra(EXTRA_BOUND_APP_ID, appId)
        val overlayExe = loadExecutable(context, appId)
        if (overlayExe.isNotEmpty()) {
            container.executablePath = overlayExe
        } else if (container.executablePath.isNotEmpty()) {
            saveExecutable(context, appId, container.executablePath)
        } else {
            rememberMember(context, appId)
        }
        container.saveData()
        val control = app.gamenative.container.ContainerControlStore.read(container.rootDir, true)
        app.gamenative.container.ContainerControlStore.write(
            container.rootDir,
            control.copy(isolation = app.gamenative.container.IsolationTier.SHARED_COMPACT),
        )
    }

    private fun overlayDir(context: Context): File =
        File(context.filesDir, OVERLAY_DIR).apply { mkdirs() }

    private fun overlayFile(context: Context, appId: String): File =
        File(overlayDir(context), "${appId.replace(Regex("[^A-Za-z0-9_.-]+"), "_")}.json")

    private fun writeOverlay(context: Context, appId: String, executablePath: String) {
        val payload = JSONObject()
            .put("appId", appId)
            .put(KEY_EXECUTABLE, executablePath)
        overlayFile(context, appId).writeText(payload.toString())
    }
}
