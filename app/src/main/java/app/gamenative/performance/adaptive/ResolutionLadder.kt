package app.gamenative.performance.adaptive

import kotlin.math.abs
import kotlin.math.roundToInt

data class RenderResolution(val width: Int, val height: Int) {
    init {
        require(width in 320..7680)
        require(height in 240..4320)
    }

    val pixels: Long get() = width.toLong() * height
    val key: String get() = "${width}x${height}"
}

/** Aspect-preserving, deterministic resolution choices used between game launches. */
class ResolutionLadder private constructor(
    val steps: List<RenderResolution>,
) {
    init {
        require(steps.isNotEmpty())
        require(steps.zipWithNext().all { (low, high) -> low.pixels < high.pixels })
    }

    fun nearestIndex(resolution: RenderResolution): Int = steps.indices.minBy { index ->
        abs(steps[index].pixels - resolution.pixels)
    }

    fun lower(resolution: RenderResolution, minimumIndex: Int = 0): RenderResolution {
        val index = nearestIndex(resolution)
        return steps[(index - 1).coerceAtLeast(minimumIndex.coerceIn(0, steps.lastIndex))]
    }

    fun higher(resolution: RenderResolution, maximumIndex: Int = steps.lastIndex): RenderResolution {
        val index = nearestIndex(resolution)
        return steps[(index + 1).coerceAtMost(maximumIndex.coerceIn(0, steps.lastIndex))]
    }

    fun normalizedPixelScale(resolution: RenderResolution): Double =
        resolution.pixels.toDouble() / steps.last().pixels.toDouble()

    companion object {
        private val DEFAULT_HEIGHT_FACTORS = doubleArrayOf(0.60, 0.70, 0.80, 0.90, 1.0)

        fun parse(value: String): RenderResolution? {
            val parts = value.lowercase().split('x')
            if (parts.size != 2) return null
            val width = parts[0].trim().toIntOrNull() ?: return null
            val height = parts[1].trim().toIntOrNull() ?: return null
            return runCatching { RenderResolution(width, height) }.getOrNull()
        }

        fun around(native: RenderResolution): ResolutionLadder {
            val aspect = native.width.toDouble() / native.height
            val unique = linkedMapOf<String, RenderResolution>()
            for (factor in DEFAULT_HEIGHT_FACTORS) {
                val height = alignEven((native.height * factor).roundToInt()).coerceAtLeast(360)
                val width = alignEven((height * aspect).roundToInt()).coerceAtLeast(640)
                val resolution = RenderResolution(width, height)
                unique[resolution.key] = resolution
            }
            unique[native.key] = native
            return ResolutionLadder(unique.values.sortedBy { it.pixels })
        }

        private fun alignEven(value: Int): Int = value and -2
    }
}
