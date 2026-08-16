package app.gamenative.performance.adaptive

import kotlin.math.abs
import kotlin.math.max

enum class PerformanceBottleneck {
    WARMUP,
    BALANCED,
    CPU,
    GPU,
    MEMORY,
    THERMAL,
    FRAME_PACING,
}

enum class ResolutionAdvice {
    HOLD,
    LOWER,
    RAISE,
}

data class AdaptiveSample(
    val timestampMs: Long,
    val targetFps: Int,
    val fps: Float,
    val frameTimeP50Ms: Float,
    val frameTimeP95Ms: Float,
    val frameTimeMaxMs: Float,
    val slowFrameRatio: Float,
    val cpuUsagePercent: Float?,
    val gpuUsagePercent: Float?,
    val cpuTempC: Int?,
    val gpuTempC: Int?,
    val availableMemoryBytes: Long?,
    val lowMemory: Boolean,
)

data class AdaptivePrediction(
    val bottleneck: PerformanceBottleneck,
    val resolutionAdvice: ResolutionAdvice,
    val predictedP95Ms: Float,
    val predictedTemperatureC: Float?,
    val confidence: Float,
    val reason: String,
)

/**
 * Lightweight online predictor used by Adaptive Engine observation mode.
 *
 * The state is constant-size and updated without collections or model files. An exponentially
 * weighted level and trend predict the next five seconds; an equally weighted residual estimates
 * confidence. The model deliberately recommends resolution changes only for a confidently
 * GPU-bound workload. It never applies the recommendation itself.
 */
class AdaptivePerformanceModel(
    private val horizonSeconds: Float = 5f,
    private val levelAlpha: Float = 0.25f,
    private val trendAlpha: Float = 0.12f,
    private val residualAlpha: Float = 0.15f,
) {
    companion object {
        private const val MIN_SAMPLES = 8
        private const val GPU_BOUND_PERCENT = 85f
        private const val GPU_IDLE_PERCENT = 70f
        private const val THERMAL_WARNING_C = 85f
        private const val THERMAL_CRITICAL_C = 88f
        private const val MEMORY_WARNING_BYTES = 768L * 1024L * 1024L
        private const val MEMORY_CRITICAL_BYTES = 512L * 1024L * 1024L
        private const val MIN_CONFIDENCE_FOR_ADVICE = 0.65f
    }

    private var samples = 0
    private var lastTimestampMs = 0L
    private var frameLevelMs = 0f
    private var frameTrendMsPerSecond = 0f
    private var frameResidualMs = 0f
    private var thermalLevelC = 0f
    private var thermalTrendCPerSecond = 0f
    private var thermalResidualC = 0f
    private var hasThermal = false
    private var consecutiveGpuBound = 0
    private var consecutiveHeadroom = 0

    init {
        require(horizonSeconds > 0f)
        require(levelAlpha in 0f..1f)
        require(trendAlpha in 0f..1f)
        require(residualAlpha in 0f..1f)
    }

    fun reset() {
        samples = 0
        lastTimestampMs = 0L
        frameLevelMs = 0f
        frameTrendMsPerSecond = 0f
        frameResidualMs = 0f
        thermalLevelC = 0f
        thermalTrendCPerSecond = 0f
        thermalResidualC = 0f
        hasThermal = false
        consecutiveGpuBound = 0
        consecutiveHeadroom = 0
    }

    fun observe(sample: AdaptiveSample): AdaptivePrediction {
        val dtSeconds = if (lastTimestampMs > 0L) {
            ((sample.timestampMs - lastTimestampMs).coerceIn(100L, 5_000L) / 1_000f)
        } else {
            1f
        }
        lastTimestampMs = sample.timestampMs

        updateFrameModel(sample.frameTimeP95Ms.coerceAtLeast(0f), dtSeconds)
        val hottest = hottestTemperature(sample)
        if (hottest != null) updateThermalModel(hottest, dtSeconds)
        samples++

        val predictedP95 = max(0f, frameLevelMs + frameTrendMsPerSecond * horizonSeconds)
        val predictedThermal = if (hasThermal) {
            max(0f, thermalLevelC + thermalTrendCPerSecond * horizonSeconds)
        } else {
            null
        }
        val confidence = confidence()
        val bottleneck = classify(sample, predictedThermal)
        updateStabilityCounters(bottleneck, sample, predictedP95)
        val advice = advise(sample, bottleneck, predictedP95, predictedThermal, confidence)

        return AdaptivePrediction(
            bottleneck = bottleneck,
            resolutionAdvice = advice,
            predictedP95Ms = predictedP95,
            predictedTemperatureC = predictedThermal,
            confidence = confidence,
            reason = reason(bottleneck, advice),
        )
    }

    private fun updateFrameModel(value: Float, dtSeconds: Float) {
        if (samples == 0) {
            frameLevelMs = value
            return
        }
        val previous = frameLevelMs
        val residual = value - previous
        frameLevelMs += levelAlpha * residual
        val observedTrend = (frameLevelMs - previous) / dtSeconds
        frameTrendMsPerSecond += trendAlpha * (observedTrend - frameTrendMsPerSecond)
        frameResidualMs += residualAlpha * (abs(residual) - frameResidualMs)
    }

    private fun updateThermalModel(value: Float, dtSeconds: Float) {
        if (!hasThermal) {
            thermalLevelC = value
            hasThermal = true
            return
        }
        val previous = thermalLevelC
        val residual = value - previous
        thermalLevelC += levelAlpha * residual
        val observedTrend = (thermalLevelC - previous) / dtSeconds
        thermalTrendCPerSecond += trendAlpha * (observedTrend - thermalTrendCPerSecond)
        thermalResidualC += residualAlpha * (abs(residual) - thermalResidualC)
    }

    private fun classify(sample: AdaptiveSample, predictedThermal: Float?): PerformanceBottleneck {
        if (samples < MIN_SAMPLES || sample.fps <= 0f || sample.frameTimeP50Ms <= 0f) {
            return PerformanceBottleneck.WARMUP
        }

        val availableMemory = sample.availableMemoryBytes
        if (sample.lowMemory || (availableMemory != null && availableMemory <= MEMORY_CRITICAL_BYTES)) {
            return PerformanceBottleneck.MEMORY
        }
        if ((predictedThermal ?: 0f) >= THERMAL_CRITICAL_C ||
            ((predictedThermal ?: 0f) >= THERMAL_WARNING_C && thermalTrendCPerSecond > 0.15f)
        ) {
            return PerformanceBottleneck.THERMAL
        }

        val deadlineMs = deadlineMs(sample.targetFps)
        val missesDeadline = sample.targetFps > 0 &&
            (sample.frameTimeP95Ms > deadlineMs * 1.08f || sample.fps < sample.targetFps - 1f)
        val gpu = sample.gpuUsagePercent
        if (missesDeadline && gpu != null && gpu >= GPU_BOUND_PERCENT) {
            return PerformanceBottleneck.GPU
        }
        if (missesDeadline && gpu != null && gpu <= GPU_IDLE_PERCENT) {
            return PerformanceBottleneck.CPU
        }
        if (sample.frameTimeP95Ms > sample.frameTimeP50Ms * 1.55f || sample.slowFrameRatio > 0.08f) {
            return PerformanceBottleneck.FRAME_PACING
        }
        if (availableMemory != null && availableMemory <= MEMORY_WARNING_BYTES) {
            return PerformanceBottleneck.MEMORY
        }
        return PerformanceBottleneck.BALANCED
    }

    private fun updateStabilityCounters(
        bottleneck: PerformanceBottleneck,
        sample: AdaptiveSample,
        predictedP95Ms: Float,
    ) {
        if (bottleneck == PerformanceBottleneck.GPU) {
            consecutiveGpuBound++
        } else {
            consecutiveGpuBound = 0
        }

        val deadline = deadlineMs(sample.targetFps)
        val hasHeadroom = bottleneck == PerformanceBottleneck.BALANCED &&
            predictedP95Ms < deadline * 0.78f &&
            (sample.gpuUsagePercent ?: 100f) < 72f
        if (hasHeadroom) consecutiveHeadroom++ else consecutiveHeadroom = 0
    }

    private fun advise(
        sample: AdaptiveSample,
        bottleneck: PerformanceBottleneck,
        predictedP95Ms: Float,
        predictedThermal: Float?,
        confidence: Float,
    ): ResolutionAdvice {
        if (sample.targetFps <= 0 || confidence < MIN_CONFIDENCE_FOR_ADVICE) {
            return ResolutionAdvice.HOLD
        }
        val deadline = deadlineMs(sample.targetFps)
        if (bottleneck == PerformanceBottleneck.GPU &&
            consecutiveGpuBound >= 4 &&
            predictedP95Ms > deadline * 1.08f
        ) {
            return ResolutionAdvice.LOWER
        }
        if (bottleneck == PerformanceBottleneck.THERMAL &&
            (sample.gpuUsagePercent ?: 0f) >= GPU_BOUND_PERCENT &&
            (predictedThermal ?: 0f) >= THERMAL_CRITICAL_C
        ) {
            return ResolutionAdvice.LOWER
        }
        if (consecutiveHeadroom >= 20) return ResolutionAdvice.RAISE
        return ResolutionAdvice.HOLD
    }

    private fun confidence(): Float {
        val maturity = (samples / 30f).coerceIn(0f, 1f)
        val frameNoise = if (frameLevelMs > 0f) (frameResidualMs / frameLevelMs).coerceIn(0f, 1f) else 1f
        val thermalNoise = if (hasThermal) (thermalResidualC / 8f).coerceIn(0f, 1f) else 0.25f
        return (maturity * (1f - 0.7f * frameNoise) * (1f - 0.3f * thermalNoise)).coerceIn(0f, 1f)
    }

    private fun hottestTemperature(sample: AdaptiveSample): Float? {
        val cpu = sample.cpuTempC?.toFloat()
        val gpu = sample.gpuTempC?.toFloat()
        return when {
            cpu != null && gpu != null -> max(cpu, gpu)
            cpu != null -> cpu
            else -> gpu
        }
    }

    private fun deadlineMs(targetFps: Int): Float = 1_000f / targetFps.coerceAtLeast(30)

    private fun reason(bottleneck: PerformanceBottleneck, advice: ResolutionAdvice): String =
        "${bottleneck.name.lowercase()}:${advice.name.lowercase()}"
}
