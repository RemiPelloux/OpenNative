package app.gamenative.powercontrol.metrics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemMetricsReaderTest {
    @Test
    fun `unsigned parser reads proc stat fields without labels`() {
        val output = LongArray(10)

        val count = parseUnsignedLongFields(
            "cpu  120 3 40 900 11 2 4 0 0 0",
            output,
            startIndex = 3,
        )

        assertEquals(10, count)
        assertArrayEquals(
            longArrayOf(120, 3, 40, 900, 11, 2, 4, 0, 0, 0),
            output,
        )
    }

    @Test
    fun `unsigned parser reads kgsl busy pair`() {
        val output = LongArray(2)

        val count = parseUnsignedLongFields("34567 100000", output)

        assertEquals(2, count)
        assertArrayEquals(longArrayOf(34567, 100000), output)
    }

    @Test
    fun `percentage parser preserves token behavior and clamps`() {
        assertEquals(73, parseFirstPercentToken("gpu: 73%"))
        assertEquals(12, parseFirstPercentToken("load=1.2"))
        assertEquals(100, parseFirstPercentToken("utilisation 145"))
        assertNull(parseFirstPercentToken("unavailable"))
    }
}
