package app.gamenative.performance.adaptive

import app.gamenative.powercontrol.metrics.MetricsSnapshot
import com.winlator.container.Container
import app.gamenative.performance.device.MemoryPressureLevel
import app.gamenative.performance.device.MemoryPressurePolicy

data class AdaptiveEngineState(
    val mode: AdaptiveResolutionMode,
    val activeResolution: RenderResolution?,
    val pendingResolution: RenderResolution?,
    val decisionReason: ResolutionDecisionReason,
    val model: FrameCostEstimate,
    val memoryPressure: MemoryPressureLevel,
    val availableResolutions: List<RenderResolution>,
    val minimumIndex: Int,
    val maximumIndex: Int,
    val requiresRestart: Boolean,
)

/** Session bridge. All generic renderer resolution changes are staged for the next launch. */
object AdaptiveEngineCoordinator {
    const val MODE_KEY = "adaptiveResolutionMode"
    const val PENDING_KEY = "adaptivePendingResolution"
    const val PREVIOUS_KEY = "adaptivePreviousResolution"
    const val PROBE_BASELINE_KEY = "adaptiveProbeBaselineP95Ms"
    const val MIN_INDEX_KEY = "adaptiveMinResolutionIndex"
    const val MAX_INDEX_KEY = "adaptiveMaxResolutionIndex"

    private var container: Container? = null
    private var ladder: ResolutionLadder? = null
    private var controller: AdaptiveResolutionController? = null
    private var rlsModel = FrameCostRlsModel()
    private var activeResolution: RenderResolution? = null

    @Volatile
    var state: AdaptiveEngineState? = null
        private set

    /** Applies only an already-staged, validated choice before XServer construction. */
    @Synchronized
    fun prepareForLaunch(container: Container): Boolean {
        val pending = ResolutionLadder.parse(container.getExtra(PENDING_KEY, "")) ?: return false
        val current = ResolutionLadder.parse(container.screenSize) ?: return false
        if (pending == current) {
            container.putExtra(PENDING_KEY, null)
            container.saveData()
            return false
        }
        container.putExtra(PREVIOUS_KEY, current.key)
        container.setScreenSize(pending.key)
        container.putExtra(PENDING_KEY, null)
        container.saveData()
        return true
    }

    @Synchronized
    fun start(container: Container) {
        this.container = container
        activeResolution = ResolutionLadder.parse(container.screenSize)
        val active = activeResolution ?: return stop()
        ladder = ResolutionLadder.around(active)
        val localLadder = ladder ?: return
        val minIndex = container.getExtra(MIN_INDEX_KEY, "0").toIntOrNull()?.coerceIn(0, localLadder.steps.lastIndex) ?: 0
        val maxIndex = container.getExtra(MAX_INDEX_KEY, localLadder.steps.lastIndex.toString())
            .toIntOrNull()?.coerceIn(minIndex, localLadder.steps.lastIndex) ?: localLadder.steps.lastIndex
        controller = AdaptiveResolutionController(localLadder, minIndex, maxIndex)
        val previous = ResolutionLadder.parse(container.getExtra(PREVIOUS_KEY, ""))
        val baseline = container.getExtra(PROBE_BASELINE_KEY, "").toFloatOrNull()
        if (previous != null && baseline != null) controller?.markProbe(previous, baseline)
        rlsModel = FrameCostRlsModel()
        val pending = ResolutionLadder.parse(container.getExtra(PENDING_KEY, ""))
        state = AdaptiveEngineState(
            mode(container), active, pending, ResolutionDecisionReason.WARMUP, rlsModel.estimate(),
            MemoryPressureLevel.NORMAL, localLadder.steps, minIndex, maxIndex, pending != null,
        )
    }

    @Synchronized
    fun observe(snapshot: MetricsSnapshot, prediction: AdaptivePrediction): AdaptiveEngineState? {
        val owner = container ?: return null
        val localLadder = ladder ?: return null
        val active = activeResolution ?: return null
        val memory = MemoryPressurePolicy.decide(
            snapshot.totalMemoryBytes,
            snapshot.availableMemoryBytes,
            snapshot.lowMemory,
        )
        val safeWindow = memory.allowModelTraining &&
            prediction.bottleneck !in setOf(
                PerformanceBottleneck.WARMUP,
                PerformanceBottleneck.MEMORY,
                PerformanceBottleneck.THERMAL,
                PerformanceBottleneck.FRAME_PACING,
            ) && snapshot.slowFrameCount.toFloat() / snapshot.totalFrameCount.coerceAtLeast(1) < 0.12f
        val model = rlsModel.observe(
            pixelScale = localLadder.normalizedPixelScale(active),
            frameTimeMs = snapshot.frameTimeP95Ms.toDouble(),
            accepted = safeWindow && prediction.bottleneck == PerformanceBottleneck.GPU,
        )

        val existingPending = ResolutionLadder.parse(owner.getExtra(PENDING_KEY, ""))
        val decision = if (existingPending == null) {
            controller?.decide(
                mode(owner),
                ResolutionControllerInput(snapshot.timestampMs, active, prediction, snapshot.frameTimeP95Ms, safeWindow),
            )
        } else null

        if (decision?.changed == true) {
            if (decision.reason == ResolutionDecisionReason.ROLLBACK_REGRESSION) {
                owner.putExtra(PREVIOUS_KEY, null)
                owner.putExtra(PROBE_BASELINE_KEY, null)
            } else {
                owner.putExtra(PREVIOUS_KEY, active.key)
                owner.putExtra(PROBE_BASELINE_KEY, snapshot.frameTimeP95Ms)
            }
            owner.putExtra(PENDING_KEY, decision.target.key)
            owner.saveData()
        }
        val pending = decision?.target?.takeIf { decision.changed } ?: existingPending
        return AdaptiveEngineState(
            mode = mode(owner),
            activeResolution = active,
            pendingResolution = pending,
            decisionReason = decision?.reason ?: ResolutionDecisionReason.HOLD,
            model = model,
            memoryPressure = memory.level,
            availableResolutions = localLadder.steps,
            minimumIndex = owner.getExtra(MIN_INDEX_KEY, "0").toIntOrNull()?.coerceIn(0, localLadder.steps.lastIndex) ?: 0,
            maximumIndex = owner.getExtra(MAX_INDEX_KEY, localLadder.steps.lastIndex.toString()).toIntOrNull()
                ?.coerceIn(0, localLadder.steps.lastIndex) ?: localLadder.steps.lastIndex,
            requiresRestart = pending != null,
        ).also { state = it }
    }

    @Synchronized
    fun setMode(mode: AdaptiveResolutionMode) {
        val owner = container ?: return
        owner.putExtra(MODE_KEY, mode.name)
        owner.saveData()
        state = state?.copy(mode = mode)
    }

    @Synchronized
    fun setBounds(minimumIndex: Int, maximumIndex: Int) {
        val owner = container ?: return
        val localLadder = ladder ?: return
        val min = minimumIndex.coerceIn(0, localLadder.steps.lastIndex)
        val max = maximumIndex.coerceIn(min, localLadder.steps.lastIndex)
        owner.putExtra(MIN_INDEX_KEY, min)
        owner.putExtra(MAX_INDEX_KEY, max)
        owner.saveData()
        controller = AdaptiveResolutionController(localLadder, min, max)
        state = state?.copy(minimumIndex = min, maximumIndex = max)
    }

    @Synchronized
    fun stageResolution(index: Int) {
        val owner = container ?: return
        val localLadder = ladder ?: return
        val target = localLadder.steps[index.coerceIn(0, localLadder.steps.lastIndex)]
        if (target == activeResolution) return discardPending()
        owner.putExtra(PREVIOUS_KEY, activeResolution?.key)
        owner.putExtra(PENDING_KEY, target.key)
        owner.saveData()
        state = state?.copy(pendingResolution = target, requiresRestart = true)
    }

    @Synchronized
    fun applyPendingForNextLaunch(): Boolean {
        val owner = container ?: return false
        if (!prepareForLaunch(owner)) return false
        state = state?.copy(pendingResolution = null, requiresRestart = true)
        return true
    }

    @Synchronized
    fun discardPending() {
        container?.apply {
            putExtra(PENDING_KEY, null)
            saveData()
        }
        state = state?.copy(pendingResolution = null, requiresRestart = false)
    }

    @Synchronized
    fun stop() {
        container = null
        ladder = null
        controller = null
        activeResolution = null
        rlsModel.reset()
        state = null
    }

    private fun mode(container: Container): AdaptiveResolutionMode = runCatching {
        AdaptiveResolutionMode.valueOf(container.getExtra(MODE_KEY, AdaptiveResolutionMode.OBSERVE.name))
    }.getOrDefault(AdaptiveResolutionMode.OBSERVE)
}
