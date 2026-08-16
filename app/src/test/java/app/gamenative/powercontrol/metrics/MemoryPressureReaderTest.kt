package app.gamenative.powercontrol.metrics

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryPressureReaderTest {
    @Test
    fun `reads swap and psi without loading proc files into collections`() {
        val root = Files.createTempDirectory("memory-pressure-reader").toFile()
        val memInfo = root.resolve("meminfo").apply {
            writeText("MemTotal: 12582912 kB\nSwapTotal: 4194304 kB\nSwapFree: 1941504 kB\n")
        }
        val pressure = root.resolve("pressure").apply {
            writeText("some avg10=7.25 avg60=2.00 avg300=1.00 total=10\nfull avg10=1.50 avg60=0.50 avg300=0.10 total=4\n")
        }

        val reading = MemoryPressureReader.read(memInfo, pressure)

        assertEquals(4_294_967_296L, reading.swapTotalBytes)
        assertEquals(2_306_867_200L, reading.swapUsedBytes)
        assertEquals(7.25f, reading.psiSomeAvg10)
        assertEquals(1.50f, reading.psiFullAvg10)
    }
}
