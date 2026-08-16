package app.gamenative.performance.device

enum class MemoryPressureLevel { NORMAL, ELEVATED, CRITICAL }

data class MemoryPressureDecision(
    val level: MemoryPressureLevel,
    val allowModelTraining: Boolean,
    val allowCacheMaintenance: Boolean,
    val suggestedGuestBudgetMb: Int?,
)

object MemoryPressurePolicy {
    fun decide(
        totalBytes: Long?,
        availableBytes: Long?,
        systemLowMemory: Boolean,
        swapUsedBytes: Long? = null,
        swapTotalBytes: Long? = null,
        psiSomeAvg10: Float? = null,
        psiFullAvg10: Float? = null,
    ): MemoryPressureDecision {
        val swapRatio = if (swapUsedBytes != null && swapTotalBytes != null && swapTotalBytes > 0L) {
            swapUsedBytes.toDouble() / swapTotalBytes
        } else 0.0
        val criticalSwap = swapUsedBytes != null && swapUsedBytes >= 2L * GIB &&
            availableBytes != null && availableBytes < 1024L * MIB
        if (systemLowMemory || (availableBytes != null && availableBytes < 512L * MIB) ||
            psiFullAvg10 != null && psiFullAvg10 >= 5f ||
            psiSomeAvg10 != null && psiSomeAvg10 >= 20f || criticalSwap ||
            swapRatio >= 0.50 && availableBytes != null && availableBytes < 1024L * MIB
        ) {
            return MemoryPressureDecision(MemoryPressureLevel.CRITICAL, false, false, conservativeBudget(totalBytes))
        }
        if (availableBytes != null && (availableBytes < 1024L * MIB ||
                totalBytes != null && availableBytes.toDouble() / totalBytes < 0.10)
            || psiSomeAvg10 != null && psiSomeAvg10 >= 5f
            || psiFullAvg10 != null && psiFullAvg10 >= 1f
            || swapRatio >= 0.20 && availableBytes != null && availableBytes < 1536L * MIB
        ) {
            return MemoryPressureDecision(MemoryPressureLevel.ELEVATED, false, false, conservativeBudget(totalBytes))
        }
        return MemoryPressureDecision(MemoryPressureLevel.NORMAL, true, true, null)
    }

    private fun conservativeBudget(totalBytes: Long?): Int? = when {
        totalBytes == null -> null
        totalBytes >= 14L * GIB -> 6144
        totalBytes >= 9L * GIB -> 4096
        totalBytes >= 6L * GIB -> 3072
        else -> 2048
    }

    private const val MIB = 1024L * 1024L
    private const val GIB = 1024L * MIB
}

/** Time-domain gate preventing one noisy Linux sample from changing adaptive behavior. */
class MemoryPressureGovernor(
    private val elevatedDelayMs: Long = 5_000L,
    private val criticalDelayMs: Long = 2_000L,
    private val recoveryDelayMs: Long = 15_000L,
) {
    private var level = MemoryPressureLevel.NORMAL
    private var candidate = MemoryPressureLevel.NORMAL
    private var candidateSinceMs = 0L
    private var restrictedBudgetMb: Int? = null

    fun reset() {
        level = MemoryPressureLevel.NORMAL
        candidate = level
        candidateSinceMs = 0L
        restrictedBudgetMb = null
    }

    fun observe(nowMs: Long, raw: MemoryPressureDecision, immediateCritical: Boolean = false): MemoryPressureDecision {
        raw.suggestedGuestBudgetMb?.let { restrictedBudgetMb = it }
        if (raw.level == level) {
            candidate = level
            candidateSinceMs = nowMs
        } else {
            if (candidate != raw.level) {
                candidate = raw.level
                candidateSinceMs = nowMs
            }
            val delay = when {
                immediateCritical && raw.level == MemoryPressureLevel.CRITICAL -> 0L
                raw.level == MemoryPressureLevel.CRITICAL -> criticalDelayMs
                raw.level == MemoryPressureLevel.ELEVATED && level == MemoryPressureLevel.NORMAL -> elevatedDelayMs
                else -> recoveryDelayMs
            }
            if (nowMs - candidateSinceMs >= delay) level = raw.level
        }
        val restricted = level != MemoryPressureLevel.NORMAL
        return MemoryPressureDecision(
            level = level,
            allowModelTraining = !restricted,
            allowCacheMaintenance = !restricted,
            suggestedGuestBudgetMb = if (restricted) restrictedBudgetMb else null,
        )
    }
}
