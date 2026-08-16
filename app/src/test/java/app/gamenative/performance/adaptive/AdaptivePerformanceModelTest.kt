package app.gamenative.performance.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePerformanceModelTest {
    private fun sample(
        timestampMs: Long,
        targetFps: Int = 30,
        fps: Float = 30f,
        p50Ms: Float = 33.3f,
        p95Ms: Float = 35f,
        gpu: Float? = 70f,
        cpuTemp: Int? = 70,
        gpuTemp: Int? = 70,
        availableMemoryBytes: Long? = 4L * 1024L * 1024L * 1024L,
        lowMemory: Boolean = false,
    ) = AdaptiveSample(
        timestampMs = timestampMs,
        targetFps = targetFps,
        fps = fps,
        frameTimeP50Ms = p50Ms,
        frameTimeP95Ms = p95Ms,
        frameTimeMaxMs = p95Ms * 1.2f,
        slowFrameRatio = 0.02f,
        cpuUsagePercent = 50f,
        gpuUsagePercent = gpu,
        cpuTempC = cpuTemp,
        gpuTempC = gpuTemp,
        availableMemoryBytes = availableMemoryBytes,
        lowMemory = lowMemory,
    )

    @Test
    fun `sustained gpu pressure recommends a lower resolution after confidence builds`() {
        val model = AdaptivePerformanceModel()
        var result: AdaptivePrediction? = null

        repeat(45) { index ->
            result = model.observe(
                sample(
                    timestampMs = index * 500L,
                    fps = 24f,
                    p50Ms = 40f,
                    p95Ms = 48f,
                    gpu = 94f,
                )
            )
        }

        assertEquals(PerformanceBottleneck.GPU, result?.bottleneck)
        assertEquals(ResolutionAdvice.LOWER, result?.resolutionAdvice)
        assertTrue((result?.confidence ?: 0f) >= 0.65f)
    }

    @Test
    fun `cpu bound misses never sacrifice resolution`() {
        val model = AdaptivePerformanceModel()
        var result: AdaptivePrediction? = null

        repeat(45) { index ->
            result = model.observe(
                sample(
                    timestampMs = index * 500L,
                    fps = 20f,
                    p50Ms = 48f,
                    p95Ms = 60f,
                    gpu = 42f,
                )
            )
        }

        assertEquals(PerformanceBottleneck.CPU, result?.bottleneck)
        assertEquals(ResolutionAdvice.HOLD, result?.resolutionAdvice)
    }

    @Test
    fun `memory pressure takes priority over gpu utilization`() {
        val model = AdaptivePerformanceModel()
        var result: AdaptivePrediction? = null

        repeat(20) { index ->
            result = model.observe(
                sample(
                    timestampMs = index * 500L,
                    fps = 20f,
                    p95Ms = 60f,
                    gpu = 96f,
                    availableMemoryBytes = 256L * 1024L * 1024L,
                    lowMemory = true,
                )
            )
        }

        assertEquals(PerformanceBottleneck.MEMORY, result?.bottleneck)
        assertEquals(ResolutionAdvice.HOLD, result?.resolutionAdvice)
    }

    @Test
    fun `uncapped sessions remain observation only`() {
        val model = AdaptivePerformanceModel()
        var result: AdaptivePrediction? = null

        repeat(45) { index ->
            result = model.observe(
                sample(
                    timestampMs = index * 500L,
                    targetFps = 0,
                    fps = 24f,
                    p95Ms = 50f,
                    gpu = 98f,
                )
            )
        }

        assertEquals(ResolutionAdvice.HOLD, result?.resolutionAdvice)
    }

    @Test
    fun `rising critical temperature is classified before frame pacing`() {
        val model = AdaptivePerformanceModel()
        var result: AdaptivePrediction? = null

        repeat(30) { index ->
            val temperature = 80 + index / 4
            result = model.observe(
                sample(
                    timestampMs = index * 500L,
                    cpuTemp = temperature,
                    gpuTemp = temperature,
                    gpu = 75f,
                )
            )
        }

        assertEquals(PerformanceBottleneck.THERMAL, result?.bottleneck)
        assertTrue((result?.predictedTemperatureC ?: 0f) >= 88f)
    }

    @Test
    fun `reset returns the predictor to warmup`() {
        val model = AdaptivePerformanceModel()
        repeat(20) { index -> model.observe(sample(index * 500L)) }
        model.reset()

        val result = model.observe(sample(20_000L))

        assertEquals(PerformanceBottleneck.WARMUP, result.bottleneck)
        assertEquals(ResolutionAdvice.HOLD, result.resolutionAdvice)
    }
}
