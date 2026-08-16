package app.gamenative.performance.shaders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderWarmupPolicyTest {
    @Test
    fun `cold caches never schedule read ahead`() {
        val budget = ShaderWarmupPolicy.decide(0L, 8L * GIB, lowMemory = false)

        assertFalse(budget.enabled)
        assertEquals("cold-cache", budget.reason)
    }

    @Test
    fun `memory pressure disables read ahead`() {
        assertFalse(ShaderWarmupPolicy.decide(512L * MIB, 1L * GIB, false).enabled)
        assertFalse(ShaderWarmupPolicy.decide(512L * MIB, 8L * GIB, true).enabled)
        assertFalse(ShaderWarmupPolicy.decide(512L * MIB, null, false).enabled)
    }

    @Test
    fun `large memory devices receive a bounded budget`() {
        val budget = ShaderWarmupPolicy.decide(512L * MIB, 8L * GIB, lowMemory = false)

        assertTrue(budget.enabled)
        assertEquals(16L * MIB, budget.maximumBytes)
        assertEquals(24, budget.maximumFiles)
    }

    @Test
    fun `budget never exceeds active cache size`() {
        val budget = ShaderWarmupPolicy.decide(3L * MIB, 8L * GIB, lowMemory = false)

        assertEquals(3L * MIB, budget.maximumBytes)
    }

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}
