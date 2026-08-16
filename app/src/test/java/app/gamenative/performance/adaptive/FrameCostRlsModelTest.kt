package app.gamenative.performance.adaptive

import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCostRlsModelTest {
    @Test
    fun `model converges on bounded fixed and gpu costs`() {
        val model = FrameCostRlsModel(exponent = 2.0)
        var estimate = model.estimate()
        repeat(120) { index ->
            val scale = if (index % 2 == 0) 1.0 else 0.64
            val observed = 9.0 + 25.0 * scale * scale
            estimate = model.observe(scale, observed, accepted = true)
        }

        assertTrue(kotlin.math.abs(estimate.fixedCostMs - 9.0) < 1.0)
        assertTrue(kotlin.math.abs(estimate.gpuCostAtNativeMs - 25.0) < 1.0)
        assertTrue(estimate.confidence > 0.85f)
    }

    @Test
    fun `rejected windows do not train the model`() {
        val model = FrameCostRlsModel()
        val before = model.estimate()
        repeat(20) { model.observe(1.0, 200.0, accepted = false) }
        val after = model.estimate()

        assertTrue(before == after)
    }
}
