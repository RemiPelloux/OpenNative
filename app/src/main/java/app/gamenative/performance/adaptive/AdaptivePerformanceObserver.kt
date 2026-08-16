package app.gamenative.performance.adaptive

import app.gamenative.powercontrol.metrics.MetricsSnapshot

/** Session-scoped owner of the observation-only 0.3.0 predictor. */
object AdaptivePerformanceObserver {
    private var model = AdaptivePerformanceModel()

    @Volatile
    var latestPrediction: AdaptivePrediction? = null
        private set

    @Synchronized
    fun start() {
        model = AdaptivePerformanceModel()
        latestPrediction = null
    }

    @Synchronized
    fun observe(snapshot: MetricsSnapshot, targetFps: Int): AdaptivePrediction {
        val totalFrames = snapshot.totalFrameCount.coerceAtLeast(1)
        val prediction = model.observe(
            AdaptiveSample(
                timestampMs = snapshot.timestampMs,
                targetFps = targetFps,
                fps = snapshot.fps,
                frameTimeP50Ms = snapshot.frameTimeP50Ms,
                frameTimeP95Ms = snapshot.frameTimeP95Ms,
                frameTimeMaxMs = snapshot.frameTimeMaxMs,
                slowFrameRatio = snapshot.slowFrameCount.toFloat() / totalFrames,
                cpuUsagePercent = snapshot.cpuUsagePercent,
                gpuUsagePercent = snapshot.gpuUsagePercent,
                cpuTempC = snapshot.cpuTempC,
                gpuTempC = snapshot.gpuTempC,
                availableMemoryBytes = snapshot.availableMemoryBytes,
                lowMemory = snapshot.lowMemory,
            )
        )
        latestPrediction = prediction
        return prediction
    }

    @Synchronized
    fun stop() {
        latestPrediction = null
        model.reset()
    }
}
