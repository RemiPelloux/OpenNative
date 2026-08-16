package app.gamenative.performance.adaptive

import kotlin.math.abs
import kotlin.math.pow

data class FrameCostEstimate(
    val fixedCostMs: Double,
    val gpuCostAtNativeMs: Double,
    val exponent: Double,
    val confidence: Float,
) {
    fun predictMs(pixelScale: Double): Double =
        (fixedCostMs + gpuCostAtNativeMs * pixelScale.coerceIn(0.2, 1.5).pow(exponent))
            .coerceAtLeast(0.0)
}

/**
 * Bounded recursive least-squares estimator for T(s) = fixed + gpu * s^p.
 *
 * Updates are accepted only from stable, GPU-bound windows selected by the coordinator. Keeping
 * the exponent fixed makes the two-parameter model identifiable from a small number of safe,
 * between-launch probes.
 */
class FrameCostRlsModel(
    private val exponent: Double = 1.85,
    private val forgettingFactor: Double = 0.985,
) {
    private var theta0 = 8.0
    private var theta1 = 20.0
    private var p00 = 100.0
    private var p01 = 0.0
    private var p10 = 0.0
    private var p11 = 100.0
    private var acceptedSamples = 0
    private var residualEwma = 0.0

    init {
        require(exponent in 1.0..2.5)
        require(forgettingFactor in 0.9..1.0)
    }

    fun reset() {
        theta0 = 8.0
        theta1 = 20.0
        p00 = 100.0
        p01 = 0.0
        p10 = 0.0
        p11 = 100.0
        acceptedSamples = 0
        residualEwma = 0.0
    }

    fun observe(pixelScale: Double, frameTimeMs: Double, accepted: Boolean): FrameCostEstimate {
        if (!accepted || !pixelScale.isFinite() || !frameTimeMs.isFinite() || frameTimeMs !in 1.0..250.0) {
            return estimate()
        }

        val x0 = 1.0
        val x1 = pixelScale.coerceIn(0.2, 1.5).pow(exponent)
        val px0 = p00 * x0 + p01 * x1
        val px1 = p10 * x0 + p11 * x1
        val denominator = (forgettingFactor + x0 * px0 + x1 * px1).coerceAtLeast(1e-6)
        val k0 = px0 / denominator
        val k1 = px1 / denominator
        val predicted = theta0 * x0 + theta1 * x1
        val residual = frameTimeMs - predicted

        theta0 = (theta0 + k0 * residual).coerceIn(0.0, 200.0)
        theta1 = (theta1 + k1 * residual).coerceIn(0.0, 240.0)

        val np00 = (p00 - k0 * (x0 * p00 + x1 * p10)) / forgettingFactor
        val np01 = (p01 - k0 * (x0 * p01 + x1 * p11)) / forgettingFactor
        val np10 = (p10 - k1 * (x0 * p00 + x1 * p10)) / forgettingFactor
        val np11 = (p11 - k1 * (x0 * p01 + x1 * p11)) / forgettingFactor
        p00 = np00.coerceIn(-10_000.0, 10_000.0)
        p01 = np01.coerceIn(-10_000.0, 10_000.0)
        p10 = np10.coerceIn(-10_000.0, 10_000.0)
        p11 = np11.coerceIn(-10_000.0, 10_000.0)

        residualEwma += 0.12 * (abs(residual) - residualEwma)
        acceptedSamples++
        return estimate()
    }

    fun estimate(): FrameCostEstimate {
        val maturity = (acceptedSamples / 40f).coerceIn(0f, 1f)
        val signal = (theta0 + theta1).coerceAtLeast(1.0)
        val noisePenalty = (residualEwma / signal).coerceIn(0.0, 1.0)
        return FrameCostEstimate(theta0, theta1, exponent, (maturity * (1.0 - noisePenalty)).toFloat())
    }
}
