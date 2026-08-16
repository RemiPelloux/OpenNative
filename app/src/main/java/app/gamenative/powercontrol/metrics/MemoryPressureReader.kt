package app.gamenative.powercontrol.metrics

import java.io.File

data class LinuxMemoryPressureReading(
    val swapTotalBytes: Long?,
    val swapUsedBytes: Long?,
    val psiSomeAvg10: Float?,
    val psiFullAvg10: Float?,
)

/** Low-allocation Linux memory-pressure sampling, called only on the slow metrics cadence. */
object MemoryPressureReader {
    fun read(
        memInfo: File = File("/proc/meminfo"),
        pressure: File = File("/proc/pressure/memory"),
    ): LinuxMemoryPressureReading {
        val (swapTotal, swapFree) = readSwap(memInfo)
        val psi = readPsi(pressure)
        return LinuxMemoryPressureReading(
            swapTotalBytes = swapTotal,
            swapUsedBytes = if (swapTotal != null && swapFree != null) {
                (swapTotal - swapFree).coerceAtLeast(0L)
            } else null,
            psiSomeAvg10 = psi.first,
            psiFullAvg10 = psi.second,
        )
    }

    internal fun readSwap(file: File): Pair<Long?, Long?> {
        var total: Long? = null
        var free: Long? = null
        runCatching {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.startsWith("SwapTotal:") -> total = kibValue(line)
                        line.startsWith("SwapFree:") -> free = kibValue(line)
                    }
                }
            }
        }
        return total to free
    }

    internal fun readPsi(file: File): Pair<Float?, Float?> {
        var some: Float? = null
        var full: Float? = null
        runCatching {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.startsWith("some ") -> some = avg10(line)
                        line.startsWith("full ") -> full = avg10(line)
                    }
                }
            }
        }
        return some to full
    }

    private fun kibValue(line: String): Long? {
        var index = line.indexOf(':') + 1
        while (index < line.length && !line[index].isDigit()) index++
        var value = 0L
        var found = false
        while (index < line.length && line[index].isDigit()) {
            found = true
            value = value * 10L + (line[index] - '0')
            index++
        }
        return if (found) value * 1024L else null
    }

    private fun avg10(line: String): Float? {
        val prefix = "avg10="
        val start = line.indexOf(prefix)
        if (start < 0) return null
        val valueStart = start + prefix.length
        val end = line.indexOf(' ', valueStart).let { if (it < 0) line.length else it }
        return line.substring(valueStart, end).toFloatOrNull()
    }
}
