package app.gamenative.performance.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPressurePolicyTest {
    private val gib = 1024L * 1024L * 1024L

    @Test
    fun `critical pressure disables training and maintenance`() {
        val decision = MemoryPressurePolicy.decide(12 * gib, 300 * 1024L * 1024L, true)
        assertEquals(MemoryPressureLevel.CRITICAL, decision.level)
        assertFalse(decision.allowModelTraining)
        assertFalse(decision.allowCacheMaintenance)
        assertEquals(4096, decision.suggestedGuestBudgetMb)
    }

    @Test
    fun `healthy memory leaves explicit settings alone`() {
        val decision = MemoryPressurePolicy.decide(12 * gib, 5 * gib, false)
        assertEquals(MemoryPressureLevel.NORMAL, decision.level)
        assertTrue(decision.allowModelTraining)
        assertNull(decision.suggestedGuestBudgetMb)
    }
}
