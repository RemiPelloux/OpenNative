package app.gamenative.compat

import java.io.File
import org.json.JSONObject

data class SessionMarker(
    val appId: String,
    val kind: String,
    val startedAtMs: Long,
)

object SafeLaunchOnce {
    @Volatile private var pendingAppId: String? = null

    fun arm(appId: String) {
        pendingAppId = appId
    }

    fun consume(appId: String): Boolean {
        val match = pendingAppId == appId
        if (match) pendingAppId = null
        return match
    }
}

object SessionRecovery {
    const val FILE_NAME = "opennative-session-marker.json"
    @Volatile private var filesDir: File? = null

    fun markStarted(dir: File, appId: String, kind: String, nowMs: Long = System.currentTimeMillis()) {
        filesDir = dir
        val body = JSONObject()
            .put("appId", appId)
            .put("kind", kind)
            .put("startedAtMs", nowMs)
        File(dir, FILE_NAME).writeText(body.toString())
    }

    fun markClean(dir: File? = filesDir) {
        val root = dir ?: return
        val file = File(root, FILE_NAME)
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("Could not clear session marker")
        }
    }

    fun pending(dir: File): SessionMarker? {
        val file = File(dir, FILE_NAME)
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val appId = json.optString("appId")
            if (appId.isBlank()) null else SessionMarker(
                appId = appId,
                kind = json.optString("kind", "game"),
                startedAtMs = json.optLong("startedAtMs"),
            )
        }.getOrNull()
    }
}
