package app.gamenative.container

import java.security.MessageDigest

data class LaunchRecipe(
    val wine: String,
    val translator: String,
    val graphics: String,
    val dxWrapper: String,
    val wincomponents: String,
    val locale: String,
    val startup: String,
    val isolation: IsolationTier,
    val profile: LaunchProfile,
) {
    fun hash(): String {
        val canonical = listOf(
            wine, translator, graphics, dxWrapper, wincomponents,
            locale, startup, isolation.wireName, profile.wireName,
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }.take(16)
    }
}

data class LaunchStageTiming(
    val stage: String,
    val durationMs: Long,
)

object ExplainLastLaunch {
    fun delta(
        previous: List<LaunchStageTiming>,
        current: List<LaunchStageTiming>,
        warmExpected: Boolean,
        winebootRan: Boolean,
    ): String {
        if (warmExpected && winebootRan) {
            return "Warm-start missed: wineboot ran because the prefix marker did not match."
        }
        if (previous.isEmpty()) {
            return "First recorded launch. Stages: ${current.joinToString(" → ") { it.stage }}."
        }
        val grown = current.mapNotNull { now ->
            val before = previous.firstOrNull { it.stage == now.stage } ?: return@mapNotNull null
            if (now.durationMs > before.durationMs + 50L) {
                "${now.stage} +${now.durationMs - before.durationMs}ms"
            } else {
                null
            }
        }
        return if (grown.isEmpty()) {
            "This launch matched the previous success."
        } else {
            "Slower than last success: ${grown.joinToString(", ")}."
        }
    }
}
