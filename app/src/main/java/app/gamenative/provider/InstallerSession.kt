package app.gamenative.provider

enum class InstallerSessionOutcome {
    RUNNING,
    QUIESCENT_SUCCESS,
    NEEDS_REVIEW,
    CANCELLED,
}

enum class InstallerReviewReason {
    PARENT_EXITED_EARLY,
    CHILD_HANG,
    TIMEOUT,
    REBOOT_REQUIRED,
    MISSING_EXECUTABLE,
    USER_CANCEL,
}

data class InstallerSessionState(
    val sessionId: String,
    val startedAtMs: Long,
    val lastFsChangeMs: Long = startedAtMs,
    val parentExitedAtMs: Long? = null,
    val winePids: Set<Int> = emptySet(),
    val outcome: InstallerSessionOutcome = InstallerSessionOutcome.RUNNING,
    val reason: InstallerReviewReason? = null,
)

object InstallerSessionMachine {
    const val QUIESCENCE_MS = 8_000L
    const val PARENT_GRACE_MS = 15_000L
    const val CHILD_HANG_MS = 120_000L
    const val SESSION_TIMEOUT_MS = 3_600_000L

    fun onChildSeen(state: InstallerSessionState, pid: Int, nowMs: Long): InstallerSessionState {
        if (state.outcome != InstallerSessionOutcome.RUNNING) return state
        return state.copy(winePids = state.winePids + pid, lastFsChangeMs = nowMs)
    }

    fun onChildExited(state: InstallerSessionState, pid: Int, nowMs: Long): InstallerSessionState {
        if (state.outcome != InstallerSessionOutcome.RUNNING) return state
        return state.copy(winePids = state.winePids - pid, lastFsChangeMs = nowMs)
    }

    fun onFilesystemChange(state: InstallerSessionState, nowMs: Long): InstallerSessionState {
        if (state.outcome != InstallerSessionOutcome.RUNNING) return state
        return state.copy(lastFsChangeMs = nowMs)
    }

    fun onParentExit(state: InstallerSessionState, nowMs: Long): InstallerSessionState {
        if (state.outcome != InstallerSessionOutcome.RUNNING) return state
        return state.copy(parentExitedAtMs = nowMs)
    }

    fun cancel(state: InstallerSessionState): InstallerSessionState =
        state.copy(outcome = InstallerSessionOutcome.CANCELLED, reason = InstallerReviewReason.USER_CANCEL)

    fun evaluate(
        state: InstallerSessionState,
        nowMs: Long,
        discoveredExe: Boolean,
        rebootHint: Boolean,
    ): InstallerSessionState {
        if (state.outcome != InstallerSessionOutcome.RUNNING) return state
        if (rebootHint) return review(state, InstallerReviewReason.REBOOT_REQUIRED)
        if (nowMs - state.startedAtMs >= SESSION_TIMEOUT_MS) {
            return review(state, InstallerReviewReason.TIMEOUT)
        }
        val parentExit = state.parentExitedAtMs ?: return state
        if (state.winePids.isNotEmpty()) {
            return if (nowMs - parentExit >= CHILD_HANG_MS) {
                review(state, InstallerReviewReason.CHILD_HANG)
            } else {
                state
            }
        }
        val quiet = nowMs - state.lastFsChangeMs >= QUIESCENCE_MS
        val graceOver = nowMs - parentExit >= PARENT_GRACE_MS
        return when {
            discoveredExe && quiet -> state.copy(outcome = InstallerSessionOutcome.QUIESCENT_SUCCESS)
            !discoveredExe && graceOver -> review(state, InstallerReviewReason.PARENT_EXITED_EARLY)
            discoveredExe && graceOver && !quiet -> review(state, InstallerReviewReason.MISSING_EXECUTABLE)
            else -> state
        }
    }

    private fun review(
        state: InstallerSessionState,
        reason: InstallerReviewReason,
    ): InstallerSessionState = state.copy(
        outcome = InstallerSessionOutcome.NEEDS_REVIEW,
        reason = reason,
    )
}
