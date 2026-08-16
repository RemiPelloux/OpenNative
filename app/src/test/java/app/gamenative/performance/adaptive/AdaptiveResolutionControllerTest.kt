package app.gamenative.performance.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveResolutionControllerTest {
    private val native = RenderResolution(1280, 720)
    private val ladder = ResolutionLadder.around(native)

    @Test
    fun `automatic lowers only after sustained gpu votes`() {
        val controller = AdaptiveResolutionController(ladder, cooldownMs = 0)
        var decision: ResolutionDecision? = null
        repeat(4) { index ->
            decision = controller.decide(
                AdaptiveResolutionMode.AUTOMATIC,
                input(index.toLong(), PerformanceBottleneck.GPU, ResolutionAdvice.LOWER),
            )
        }

        assertTrue(decision!!.changed)
        assertEquals(ResolutionDecisionReason.LOWER_FOR_GPU, decision!!.reason)
        assertTrue(decision!!.target.pixels < native.pixels)
    }

    @Test
    fun `cpu and memory stalls never lower resolution`() {
        for (bottleneck in listOf(PerformanceBottleneck.CPU, PerformanceBottleneck.MEMORY)) {
            val controller = AdaptiveResolutionController(ladder, cooldownMs = 0)
            repeat(20) { index ->
                val decision = controller.decide(
                    AdaptiveResolutionMode.AUTOMATIC,
                    input(index.toLong(), bottleneck, ResolutionAdvice.LOWER),
                )
                assertFalse(decision.changed)
            }
        }
    }

    @Test
    fun `unsafe and observation windows never mutate resolution`() {
        val controller = AdaptiveResolutionController(ladder, cooldownMs = 0)
        repeat(8) { index ->
            assertFalse(
                controller.decide(
                    AdaptiveResolutionMode.AUTOMATIC,
                    input(index.toLong(), PerformanceBottleneck.GPU, ResolutionAdvice.LOWER, safe = false),
                ).changed,
            )
            assertFalse(
                controller.decide(
                    AdaptiveResolutionMode.OBSERVE,
                    input(index.toLong(), PerformanceBottleneck.GPU, ResolutionAdvice.LOWER),
                ).changed,
            )
        }
    }

    @Test
    fun `failed probe rolls back to previous resolution`() {
        val current = ladder.lower(native)
        val controller = AdaptiveResolutionController(ladder, cooldownMs = 0)
        controller.markProbe(native, baselineP95Ms = 35f)

        val decision = controller.decide(
            AdaptiveResolutionMode.AUTOMATIC,
            input(10_000, PerformanceBottleneck.BALANCED, ResolutionAdvice.HOLD, current, p95 = 40f),
        )

        assertTrue(decision.changed)
        assertEquals(native, decision.target)
        assertEquals(ResolutionDecisionReason.ROLLBACK_REGRESSION, decision.reason)
    }

    private fun input(
        timestamp: Long,
        bottleneck: PerformanceBottleneck,
        advice: ResolutionAdvice,
        current: RenderResolution = native,
        p95: Float = 40f,
        safe: Boolean = true,
    ) = ResolutionControllerInput(
        timestampMs = timestamp,
        current = current,
        prediction = AdaptivePrediction(
            bottleneck = bottleneck,
            resolutionAdvice = advice,
            predictedP95Ms = p95,
            predictedTemperatureC = 75f,
            confidence = 0.95f,
            reason = "test",
        ),
        frameTimeP95Ms = p95,
        safeWindow = safe,
    )
}
