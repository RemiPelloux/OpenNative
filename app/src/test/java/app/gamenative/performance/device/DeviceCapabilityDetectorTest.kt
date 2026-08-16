package app.gamenative.performance.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilityDetectorTest {
    @Test
    fun `detects Snapdragon only from combined capabilities`() {
        val profile = DeviceCapabilityDetector.detect(
            manufacturer = "AYN",
            model = "Thor",
            hardware = "qcom",
            board = "kalama",
            soc = "SM8550",
            gpu = "Adreno (TM) 740",
            performanceHintAvailable = true,
            clusters = emptyList(),
        )
        assertTrue(profile.isSnapdragonAdreno)
        assertTrue(profile.performanceHintAvailable)

        val mali = profile.copy(gpu = "Mali-G715", isAdreno = false)
        assertFalse(mali.isSnapdragonAdreno)
    }

    @Test
    fun `cpu list parser supports ranges and rejects malformed spans`() {
        assertEquals(listOf(0, 1, 2, 4, 6, 7), DeviceCapabilityDetector.parseCpuList("0-2 4,6-7"))
        assertEquals(emptyList<Int>(), DeviceCapabilityDetector.parseCpuList("9-1 bad"))
    }
}
