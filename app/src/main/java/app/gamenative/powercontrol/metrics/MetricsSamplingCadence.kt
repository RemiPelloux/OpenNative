package app.gamenative.powercontrol.metrics

data class MetricsSamplingDecision(
    val sampleResources: Boolean,
    val sampleThermals: Boolean,
    val writeLog: Boolean,
)

/**
 * Keeps expensive system reads and disk logging off the 500 ms frame-statistics path.
 */
class MetricsSamplingCadence(
    private val resourceIntervalMs: Long = 1_000L,
    private val thermalIntervalMs: Long = 2_000L,
    private val logIntervalMs: Long = 2_000L,
) {
    private var nextResourceSampleMs = 0L
    private var nextThermalSampleMs = 0L
    private var nextLogMs = 0L

    init {
        require(resourceIntervalMs > 0L)
        require(thermalIntervalMs > 0L)
        require(logIntervalMs > 0L)
    }

    fun reset() {
        nextResourceSampleMs = 0L
        nextThermalSampleMs = 0L
        nextLogMs = 0L
    }

    fun decide(nowMs: Long): MetricsSamplingDecision {
        val resourcesDue = nowMs >= nextResourceSampleMs
        val thermalsDue = nowMs >= nextThermalSampleMs
        val logDue = nowMs >= nextLogMs

        if (resourcesDue) nextResourceSampleMs = nowMs + resourceIntervalMs
        if (thermalsDue) nextThermalSampleMs = nowMs + thermalIntervalMs
        if (logDue) nextLogMs = nowMs + logIntervalMs

        return MetricsSamplingDecision(resourcesDue, thermalsDue, logDue)
    }
}
