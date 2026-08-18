package app.gamenative.utils

import android.content.Context
import androidx.compose.ui.graphics.Color
import app.gamenative.R

/**
 * Compatibility model for local and future OpenNative data sources.
 */
object GameCompatibilityService {
    /**
     * Data class for API request.
     */
    data class GameCompatibilityRequest(
        val gameNames: List<String>,
        val gpuName: String
    )

    /**
     * Data class for API response per game.
     */
    data class GameCompatibilityResponse(
        val gameName: String,
        val totalPlayableCount: Int,
        val gpuPlayableCount: Int,
        val avgRating: Float,
        val hasBeenTried: Boolean,
        val isNotWorking: Boolean
    )

    /**
     * Compatibility message with text and color.
     */
    data class CompatibilityMessage(
        val text: String,
        val color: Color
    )

    /**
     * Gets user-friendly compatibility message based on compatibility response.
     * Uses totalPlayableCount and gpuPlayableCount to determine the message.
     */
    fun getCompatibilityMessageFromResponse(context: Context, response: GameCompatibilityResponse): CompatibilityMessage {
        return when {
            response.totalPlayableCount > 0 && response.gpuPlayableCount > 0 ->
                CompatibilityMessage(context.getString(R.string.best_config_exact_gpu_match), Color.Green)
            response.gpuPlayableCount == 0 && response.totalPlayableCount > 0 ->
                CompatibilityMessage(context.getString(R.string.best_config_fallback_match), Color.Yellow)
            response.isNotWorking ->
                CompatibilityMessage(context.getString(R.string.library_not_compatible), Color.Red)
            else ->
                CompatibilityMessage(context.getString(R.string.library_compatibility_unknown), Color.Gray)
        }
    }

    /**
     * Fetches compatibility information for a batch of games.
     * Returns a map of game name to compatibility response, or null on error.
     */
    suspend fun fetchCompatibility(
        gameNames: List<String>,
        gpuName: String
    ): Map<String, GameCompatibilityResponse>? = emptyMap()
}
