package app.gamenative.performance.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionLadderTest {
    @Test
    fun `ladder preserves aspect ratio and native ceiling`() {
        val native = RenderResolution(1280, 720)
        val ladder = ResolutionLadder.around(native)

        assertEquals(native, ladder.steps.last())
        assertTrue(ladder.steps.size >= 4)
        assertTrue(ladder.steps.zipWithNext().all { (low, high) -> low.pixels < high.pixels })
        ladder.steps.forEach { resolution ->
            assertTrue(kotlin.math.abs(resolution.width.toFloat() / resolution.height - 16f / 9f) < 0.01f)
        }
    }

    @Test
    fun `parser rejects malformed and dangerous dimensions`() {
        assertEquals(RenderResolution(1280, 720), ResolutionLadder.parse("1280x720"))
        assertEquals(null, ResolutionLadder.parse("native"))
        assertEquals(null, ResolutionLadder.parse("10x10"))
        assertEquals(null, ResolutionLadder.parse("1280x720x32"))
    }
}
