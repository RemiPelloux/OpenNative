package app.gamenative.performance.shaders

import com.winlator.container.Container
import com.winlator.core.ShaderCacheManager

enum class ShaderWarmth { COLD, WARM, GROWING, UNAVAILABLE }

data class ShaderHealthState(
    val warmth: ShaderWarmth,
    val activeGeneration: String?,
    val activeBytes: Long,
    val activeFiles: Int,
    val sessionAddedBytes: Long,
    val sessionAddedFiles: Int,
    val inactiveBytes: Long,
    val inactiveGenerations: Int,
)

object ShaderHealthMonitor {
    private const val DEFAULT_CACHE_LIMIT_BYTES = 2L * 1024L * 1024L * 1024L
    private const val DEFAULT_INACTIVE_GENERATIONS = 3
    private var sessionContainer: Container? = null
    private var maintenanceRequested = false

    @Volatile
    var state = ShaderHealthState(ShaderWarmth.UNAVAILABLE, null, 0, 0, 0, 0, 0, 0)
        private set

    fun sessionStarted(container: Container, paths: ShaderCacheManager.CachePaths, initial: ShaderCacheManager.CacheStats) {
        sessionContainer = container
        maintenanceRequested = false
        state = ShaderHealthState(
            warmth = if (initial.files() > 0) ShaderWarmth.WARM else ShaderWarmth.COLD,
            activeGeneration = paths.generation(),
            activeBytes = initial.bytes(),
            activeFiles = initial.files(),
            sessionAddedBytes = 0,
            sessionAddedFiles = 0,
            inactiveBytes = state.inactiveBytes,
            inactiveGenerations = state.inactiveGenerations,
        )
    }

    fun sessionFinished(finalStats: ShaderCacheManager.CacheStats, result: ShaderCacheManager.CacheSessionResult) {
        state = state.copy(
            warmth = if (result.wroteCache()) ShaderWarmth.GROWING else ShaderWarmth.WARM,
            activeBytes = finalStats.bytes(),
            activeFiles = finalStats.files(),
            sessionAddedBytes = result.addedBytes(),
            sessionAddedFiles = result.addedFiles(),
        )
    }

    fun inspect(container: Container): ShaderHealthState {
        val health = ShaderCacheManager.inspectHealth(container)
        return state.copy(
            activeGeneration = health.activeGeneration(),
            activeBytes = health.active().bytes(),
            activeFiles = health.active().files(),
            inactiveBytes = health.inactiveBytes(),
            inactiveGenerations = health.inactiveGenerations(),
        ).also { state = it }
    }

    fun prune(container: Container, maximumTotalBytes: Long, maximumInactiveGenerations: Int) =
        ShaderCacheManager.pruneInactive(container, maximumTotalBytes, maximumInactiveGenerations).also {
            inspect(container)
        }

    fun requestMaintenanceAfterExit() {
        if (sessionContainer != null) maintenanceRequested = true
    }

    fun performPendingMaintenance() {
        val owner = sessionContainer
        if (owner != null && maintenanceRequested) {
            prune(owner, DEFAULT_CACHE_LIMIT_BYTES, DEFAULT_INACTIVE_GENERATIONS)
        }
        maintenanceRequested = false
        sessionContainer = null
    }

    fun resetSession() {
        state = state.copy(warmth = ShaderWarmth.UNAVAILABLE, sessionAddedBytes = 0, sessionAddedFiles = 0)
    }
}
