package app.gamenative.performance.adaptive

enum class AdaptiveResolutionMode { FIXED, OBSERVE, AUTOMATIC }

enum class ResolutionDecisionReason {
    HOLD,
    WARMUP,
    LOW_CONFIDENCE,
    UNSAFE_WINDOW,
    COOLDOWN,
    LOWER_FOR_GPU,
    RAISE_FOR_HEADROOM,
    ROLLBACK_REGRESSION,
}

data class ResolutionControllerInput(
    val timestampMs: Long,
    val current: RenderResolution,
    val prediction: AdaptivePrediction,
    val frameTimeP95Ms: Float,
    val safeWindow: Boolean,
)

data class ResolutionDecision(
    val target: RenderResolution,
    val changed: Boolean,
    val reason: ResolutionDecisionReason,
    val requiresRestart: Boolean = true,
)

/** Pure policy: bounded steps, confidence gate, hysteresis, cooldown and probe rollback. */
class AdaptiveResolutionController(
    private val ladder: ResolutionLadder,
    private val minimumIndex: Int = 0,
    private val maximumIndex: Int = ladder.steps.lastIndex,
    private val cooldownMs: Long = 120_000L,
) {
    private var lastDecisionMs = Long.MIN_VALUE
    private var lowerVotes = 0
    private var raiseVotes = 0
    private var probeBaselineP95Ms: Float? = null
    private var probePrevious: RenderResolution? = null

    fun markProbe(previous: RenderResolution, baselineP95Ms: Float) {
        probePrevious = previous
        probeBaselineP95Ms = baselineP95Ms.takeIf { it > 0f }
    }

    fun decide(mode: AdaptiveResolutionMode, input: ResolutionControllerInput): ResolutionDecision {
        if (mode != AdaptiveResolutionMode.AUTOMATIC) return hold(input, ResolutionDecisionReason.HOLD)
        if (!input.safeWindow) return hold(input, ResolutionDecisionReason.UNSAFE_WINDOW)
        if (input.prediction.bottleneck == PerformanceBottleneck.WARMUP) {
            return hold(input, ResolutionDecisionReason.WARMUP)
        }

        val baseline = probeBaselineP95Ms
        val previous = probePrevious
        if (baseline != null && previous != null && input.frameTimeP95Ms > baseline * 1.08f) {
            probeBaselineP95Ms = null
            probePrevious = null
            recordDecision(input.timestampMs)
            return ResolutionDecision(previous, previous != input.current, ResolutionDecisionReason.ROLLBACK_REGRESSION)
        }

        if (input.prediction.confidence < 0.70f) {
            return hold(input, ResolutionDecisionReason.LOW_CONFIDENCE)
        }
        if (lastDecisionMs != Long.MIN_VALUE && input.timestampMs - lastDecisionMs < cooldownMs) {
            return hold(input, ResolutionDecisionReason.COOLDOWN)
        }

        when (input.prediction.resolutionAdvice) {
            ResolutionAdvice.LOWER -> {
                lowerVotes++
                raiseVotes = 0
                if (input.prediction.bottleneck == PerformanceBottleneck.GPU && lowerVotes >= 4) {
                    val target = ladder.lower(input.current, minimumIndex)
                    if (target != input.current) {
                        markProbe(input.current, input.frameTimeP95Ms)
                        recordDecision(input.timestampMs)
                        return ResolutionDecision(target, true, ResolutionDecisionReason.LOWER_FOR_GPU)
                    }
                }
            }
            ResolutionAdvice.RAISE -> {
                raiseVotes++
                lowerVotes = 0
                if (input.prediction.bottleneck == PerformanceBottleneck.BALANCED && raiseVotes >= 12) {
                    val target = ladder.higher(input.current, maximumIndex)
                    if (target != input.current) {
                        markProbe(input.current, input.frameTimeP95Ms)
                        recordDecision(input.timestampMs)
                        return ResolutionDecision(target, true, ResolutionDecisionReason.RAISE_FOR_HEADROOM)
                    }
                }
            }
            ResolutionAdvice.HOLD -> {
                lowerVotes = 0
                raiseVotes = 0
            }
        }
        return hold(input, ResolutionDecisionReason.HOLD)
    }

    private fun recordDecision(timestampMs: Long) {
        lastDecisionMs = timestampMs
        lowerVotes = 0
        raiseVotes = 0
    }

    private fun hold(input: ResolutionControllerInput, reason: ResolutionDecisionReason) =
        ResolutionDecision(input.current, false, reason)
}
