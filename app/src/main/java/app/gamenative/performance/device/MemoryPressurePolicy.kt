package app.gamenative.performance.device

enum class MemoryPressureLevel { NORMAL, ELEVATED, CRITICAL }

data class MemoryPressureDecision(
    val level: MemoryPressureLevel,
    val allowModelTraining: Boolean,
    val allowCacheMaintenance: Boolean,
    val suggestedGuestBudgetMb: Int?,
)

object MemoryPressurePolicy {
    fun decide(totalBytes: Long?, availableBytes: Long?, systemLowMemory: Boolean): MemoryPressureDecision {
        if (systemLowMemory || (availableBytes != null && availableBytes < 512L * MIB)) {
            return MemoryPressureDecision(MemoryPressureLevel.CRITICAL, false, false, conservativeBudget(totalBytes))
        }
        if (availableBytes != null && (availableBytes < 1024L * MIB ||
                totalBytes != null && availableBytes.toDouble() / totalBytes < 0.10)
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
