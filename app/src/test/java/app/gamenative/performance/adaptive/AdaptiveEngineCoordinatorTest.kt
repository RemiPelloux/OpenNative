package app.gamenative.performance.adaptive

import com.winlator.container.Container
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdaptiveEngineCoordinatorTest {
    @After
    fun cleanUp() = AdaptiveEngineCoordinator.stop()

    @Test
    fun `new containers default to observation and never stage a change`() {
        val container = container("observe")
        AdaptiveEngineCoordinator.start(container)

        repeat(50) { index ->
            AdaptiveEngineCoordinator.observe(
                snapshot(index * 500L),
                prediction(PerformanceBottleneck.GPU, ResolutionAdvice.LOWER),
            )
        }

        assertEquals(AdaptiveResolutionMode.OBSERVE, AdaptiveEngineCoordinator.state?.mode)
        assertEquals("", container.getExtra(AdaptiveEngineCoordinator.PENDING_KEY, ""))
        assertEquals("1280x720", container.screenSize)
    }

    @Test
    fun `automatic stages next launch and prepare applies it atomically`() {
        val container = container("automatic")
        container.putExtra(AdaptiveEngineCoordinator.MODE_KEY, AdaptiveResolutionMode.AUTOMATIC.name)
        AdaptiveEngineCoordinator.start(container)

        repeat(45) { index ->
            AdaptiveEngineCoordinator.observe(
                snapshot(index * 5_000L),
                prediction(PerformanceBottleneck.GPU, ResolutionAdvice.LOWER),
            )
        }

        val pending = container.getExtra(AdaptiveEngineCoordinator.PENDING_KEY, "")
        assertTrue(pending.isNotEmpty())
        assertEquals("1280x720", container.screenSize)
        AdaptiveEngineCoordinator.stop()

        assertTrue(AdaptiveEngineCoordinator.prepareForLaunch(container))
        assertEquals(pending, container.screenSize)
        assertEquals("", container.getExtra(AdaptiveEngineCoordinator.PENDING_KEY, ""))
        assertFalse(AdaptiveEngineCoordinator.prepareForLaunch(container))
    }

    private fun container(id: String) = Container(id).apply {
        rootDir = Files.createTempDirectory("adaptive-$id").toFile()
        setScreenSize("1280x720")
    }

    private fun snapshot(timestamp: Long) = app.gamenative.powercontrol.metrics.MetricsSnapshot(
        timestampMs = timestamp,
        fps = 24f,
        frameTimeP50Ms = 40f,
        frameTimeP95Ms = 48f,
        frameTimeMaxMs = 55f,
        slowFrameCount = 2,
        totalFrameCount = 60,
        cpuUsagePercent = 50f,
        cpuUsageSource = app.gamenative.powercontrol.metrics.CpuUsageSource.PROC_STAT,
        gpuUsagePercent = 94f,
        cpuTempC = 72,
        gpuTempC = 74,
        availableMemoryBytes = 4L * 1024 * 1024 * 1024,
        totalMemoryBytes = 12L * 1024 * 1024 * 1024,
        lowMemory = false,
    )

    private fun prediction(bottleneck: PerformanceBottleneck, advice: ResolutionAdvice) =
        AdaptivePrediction(bottleneck, advice, 48f, 75f, 0.95f, "test")
}
