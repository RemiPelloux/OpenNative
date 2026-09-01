package app.gamenative.container

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object LastLaunchStore {
    const val FILE_NAME = ".opennative-last-launch.json"

    fun file(containerRoot: File): File = File(containerRoot, FILE_NAME)

    fun read(containerRoot: File): List<LaunchStageTiming> {
        val file = file(containerRoot)
        if (!file.isFile) return emptyList()
        val array = runCatching { JSONObject(file.readText()).optJSONArray("stages") }.getOrNull()
            ?: return emptyList()
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            LaunchStageTiming(obj.optString("stage"), obj.optLong("durationMs"))
        }
    }

    fun write(containerRoot: File, stages: List<LaunchStageTiming>) {
        containerRoot.mkdirs()
        val array = JSONArray()
        stages.forEach { stage ->
            array.put(JSONObject().put("stage", stage.stage).put("durationMs", stage.durationMs))
        }
        file(containerRoot).writeText(JSONObject().put("stages", array).toString())
    }
}

object VolumeHealth {
    fun isMissing(path: String): Boolean {
        if (path.isBlank()) return false
        return !File(path).exists()
    }

    fun hint(path: String): String = "volume missing at granted location"
}
