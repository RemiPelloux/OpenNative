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

    @Test
    fun `swap thrashing under low available memory is critical`() {
        val decision = MemoryPressurePolicy.decide(
            totalBytes = 12 * gib,
            availableBytes = 700 * 1024L * 1024L,
            systemLowMemory = false,
            swapUsedBytes = 2200 * 1024L * 1024L,
            swapTotalBytes = 4 * gib,
        )

        assertEquals(MemoryPressureLevel.CRITICAL, decision.level)
        assertFalse(decision.allowModelTraining)
    }

    @Test
    fun `psi stalls detect pressure before android low memory`() {
        val decision = MemoryPressurePolicy.decide(
            totalBytes = 12 * gib,
            availableBytes = 3 * gib,
            systemLowMemory = false,
            psiSomeAvg10 = 7f,
        )

        assertEquals(MemoryPressureLevel.ELEVATED, decision.level)
    }

    @Test
    fun `governor requires sustained pressure and delayed recovery`() {
        val governor = MemoryPressureGovernor(
            elevatedDelayMs = 5_000L,
            criticalDelayMs = 2_000L,
            recoveryDelayMs = 15_000L,
        )
        val elevated = MemoryPressureDecision(MemoryPressureLevel.ELEVATED, false, false, 4096)
        val normal = MemoryPressureDecision(MemoryPressureLevel.NORMAL, true, true, null)

        assertEquals(MemoryPressureLevel.NORMAL, governor.observe(0L, elevated).level)
        assertEquals(MemoryPressureLevel.NORMAL, governor.observe(4_999L, elevated).level)
        assertEquals(MemoryPressureLevel.ELEVATED, governor.observe(5_000L, elevated).level)
        assertEquals(MemoryPressureLevel.ELEVATED, governor.observe(6_000L, normal).level)
        assertEquals(MemoryPressureLevel.NORMAL, governor.observe(21_000L, normal).level)
    }
}
