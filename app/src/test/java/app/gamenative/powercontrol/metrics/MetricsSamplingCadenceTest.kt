package app.gamenative.powercontrol.metrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsSamplingCadenceTest {
    @Test
    fun `expensive samples run on independent cadences`() {
        val cadence = MetricsSamplingCadence(
            resourceIntervalMs = 1_000L,
            thermalIntervalMs = 2_000L,
            logIntervalMs = 2_000L,
        )

        val first = cadence.decide(100L)
        assertTrue(first.sampleResources)
        assertTrue(first.sampleThermals)
        assertTrue(first.writeLog)

        val fast = cadence.decide(600L)
        assertFalse(fast.sampleResources)
        assertFalse(fast.sampleThermals)
        assertFalse(fast.writeLog)

        val resources = cadence.decide(1_100L)
        assertTrue(resources.sampleResources)
        assertFalse(resources.sampleThermals)
        assertFalse(resources.writeLog)

        val slow = cadence.decide(2_100L)
        assertTrue(slow.sampleResources)
        assertTrue(slow.sampleThermals)
        assertTrue(slow.writeLog)
    }

    @Test
    fun `reset makes every source immediately due`() {
        val cadence = MetricsSamplingCadence()
        cadence.decide(5_000L)
        cadence.reset()

        val result = cadence.decide(5_001L)
        assertTrue(result.sampleResources)
        assertTrue(result.sampleThermals)
        assertTrue(result.writeLog)
    }
}
