package app.gamenative.utils

import app.gamenative.data.GameSource
import org.json.JSONObject

/**
 * Parses compatibility statistics from a future OpenNative-owned source.
 *
 * Two endpoints share the same response shape - one keyed by device + GPU, one keyed by GPU only:
 *
 *   { "games": { "STEAM": { "Balatro": [n, mfps, s5, secs], ... }, "EPIC": {…}, ... } }
 *
 * where n = successful runs, mfps = median fps, s5 = 5-star reviews, secs = median session length.
 * The server filters modern/legacy results based on the modernBuild query param and returns them
 * under "games" (we also honor a "games_modern" key if a future response provides one).
 */
object DeviceGameStatsService {

    data class DeviceGameStats(
        val successfulRuns: Int,
        val medianFps: Int,
        val fiveStarReviews: Int,
        val medianSessionSec: Int,
    )

    /** Stats for the current device + GPU. */
    suspend fun fetchForDevice(
        deviceModel: String,
        gpuName: String,
        modernBuild: Boolean,
    ): Map<GameSource, Map<String, DeviceGameStats>>? {
        return null
    }

    /** Stats for the current GPU across all devices. */
    suspend fun fetchForGpu(
        gpuName: String,
        modernBuild: Boolean,
    ): Map<GameSource, Map<String, DeviceGameStats>>? {
        return null
    }

    private fun parse(json: JSONObject, modernBuild: Boolean): Map<GameSource, Map<String, DeviceGameStats>> {
        val games = (if (modernBuild) json.optJSONObject("games_modern") else null)
            ?: json.optJSONObject("games")
            ?: return emptyMap()
        val output = mutableMapOf<GameSource, Map<String, DeviceGameStats>>()

        for (platformKey in games.keys()) {
            val source = runCatching { GameSource.valueOf(platformKey) }.getOrNull() ?: continue
            val platformGames = games.optJSONObject(platformKey) ?: continue

            val stats = mutableMapOf<String, DeviceGameStats>()
            for (gameName in platformGames.keys()) {
                val arr = platformGames.optJSONArray(gameName) ?: continue
                stats[gameName] = DeviceGameStats(
                    successfulRuns = arr.optInt(0, 0),
                    medianFps = arr.optInt(1, 0),
                    fiveStarReviews = arr.optInt(2, 0),
                    medianSessionSec = arr.optInt(3, 0),
                )
            }
            output[source] = stats
        }

        return output
    }
}
