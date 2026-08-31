package app.gamenative.ui.util

import java.io.File
import org.json.JSONObject

enum class ProfileCategory {
    GRAPHICS,
    RUNTIME,
    CONTROLLER,
    DISPLAY,
    PERFORMANCE,
    OTHER,
}

data class ProfileDiffEntry(
    val key: String,
    val category: ProfileCategory,
    val current: String,
    val incoming: String,
)

data class ProfileImportPlan(
    val diffs: List<ProfileDiffEntry>,
    val unsupported: List<String>,
)

object ProfileImport {
    fun preview(current: JSONObject, incoming: JSONObject): ProfileImportPlan {
        val keys = LinkedHashSet<String>()
        current.keys().forEach { keys.add(it) }
        incoming.keys().forEach { keys.add(it) }
        val diffs = ArrayList<ProfileDiffEntry>()
        val unsupported = ArrayList<String>()
        keys.filter { it != "schema" && it != "schemaVersion" }.forEach { key ->
            val category = categoryOf(key)
            if (category == ProfileCategory.OTHER && incoming.has(key) && !current.has(key)) {
                unsupported.add(key)
            }
            val before = current.opt(key)?.toString().orEmpty()
            val after = incoming.opt(key)?.toString().orEmpty()
            if (before != after) {
                diffs.add(ProfileDiffEntry(key, category, before, after))
            }
        }
        return ProfileImportPlan(diffs.sortedBy { it.key }, unsupported.sorted())
    }

    fun merge(
        current: JSONObject,
        incoming: JSONObject,
        categories: Set<ProfileCategory>,
        replace: Boolean,
    ): JSONObject {
        val result = JSONObject(current.toString())
        incoming.keys().forEach { key ->
            if (key == "schema" || key == "schemaVersion") {
                result.put(key, incoming.get(key))
                return@forEach
            }
            val category = categoryOf(key)
            if (category !in categories) return@forEach
            if (replace || result.opt(key)?.toString() != incoming.opt(key)?.toString()) {
                result.put(key, incoming.get(key))
            }
        }
        return result
    }

    fun backupFile(dir: File, appId: String): File {
        val safe = appId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "profile-backup-$safe.json")
    }

    fun writeBackup(file: File, json: JSONObject) {
        file.parentFile?.mkdirs()
        file.writeText(json.toString())
    }

    fun readBackup(file: File): JSONObject? {
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    fun categoryOf(key: String): ProfileCategory = when (key) {
        "graphicsDriver", "graphicsDriverVersion", "graphicsDriverConfig",
        "dxwrapper", "dxwrapperConfig", "renderer", "csmt", "useDRI3",
        "videoMemorySize", "videoPciDeviceID", "offScreenRenderingMode",
        "strictShaderMath", "sharpnessEffect", "sharpnessLevel", "sharpnessDenoise",
        -> ProfileCategory.GRAPHICS
        "containerVariant", "wineVersion", "emulator", "box64Version", "box86Version",
        "box64Preset", "box86Preset", "fexcoreVersion", "fexcoreTSOMode",
        "fexcoreX87Mode", "fexcoreMultiBlock", "fexcorePreset", "wow64Mode",
        "wincomponents", "envVars",
        -> ProfileCategory.RUNTIME
        "sdlControllerAPI", "useSteamInput", "enableXInput", "enableDInput",
        "dinputMapperType", "disableMouseInput", "touchscreenMode", "shooterMode",
        -> ProfileCategory.CONTROLLER
        "screenSize", "displayRenderer", "sfCompatMode", "portraitMode",
        "externalDisplayMode", "externalDisplaySwap",
        -> ProfileCategory.DISPLAY
        "rendererPresentMode", "showFPS", "lsfgEnabled", "fpsLimiterEnabled",
        "fpsLimiterTarget", "cpuList", "cpuListWoW64", "pulseaudioLowLatency",
        "audioDriver",
        -> ProfileCategory.PERFORMANCE
        else -> ProfileCategory.OTHER
    }
}
