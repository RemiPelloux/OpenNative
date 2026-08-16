package app.gamenative.performance.shaders

data class ShaderWarmupBudget(
    val maximumBytes: Long,
    val maximumFiles: Int,
    val reason: String,
) {
    val enabled: Boolean get() = maximumBytes > 0L && maximumFiles > 0
}

/**
 * Bounds shader read-ahead from the previous clean session. This is deliberately conservative:
 * a kernel hint must never compete with a game when Android is already short on memory.
 */
object ShaderWarmupPolicy {
    fun decide(
        activeCacheBytes: Long,
        availableMemoryBytes: Long?,
        lowMemory: Boolean,
    ): ShaderWarmupBudget {
        if (activeCacheBytes <= 0L) return ShaderWarmupBudget(0L, 0, "cold-cache")
        if (lowMemory) return ShaderWarmupBudget(0L, 0, "android-low-memory")
        val available = availableMemoryBytes
            ?: return ShaderWarmupBudget(0L, 0, "memory-unavailable")
        val limits = when {
            available < 1536L * MIB -> 0L to 0
            available < 3L * GIB -> 4L * MIB to 8
            available < 6L * GIB -> 8L * MIB to 16
            else -> 16L * MIB to 24
        }
        val bytes = minOf(activeCacheBytes, limits.first)
        return if (bytes > 0L) {
            ShaderWarmupBudget(bytes, limits.second, "recent-clean-session")
        } else {
            ShaderWarmupBudget(0L, 0, "memory-pressure")
        }
    }

    private const val MIB = 1024L * 1024L
    private const val GIB = 1024L * MIB
}
