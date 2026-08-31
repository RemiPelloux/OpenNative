package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallerSessionTest {
    @Test
    fun `parent exit without an exe needs review instead of success`() {
        val started = InstallerSessionState("s1", startedAtMs = 0L)
        val afterParent = InstallerSessionMachine.onParentExit(started, 1_000L)
        val evaluated = InstallerSessionMachine.evaluate(
            state = afterParent,
            nowMs = 1_000L + InstallerSessionMachine.PARENT_GRACE_MS,
            discoveredExe = false,
            rebootHint = false,
        )
        assertEquals(InstallerSessionOutcome.NEEDS_REVIEW, evaluated.outcome)
        assertEquals(InstallerReviewReason.PARENT_EXITED_EARLY, evaluated.reason)
    }

    @Test
    fun `quiet parent exit with a discovered exe succeeds`() {
        val started = InstallerSessionState("s1", startedAtMs = 0L, lastFsChangeMs = 0L)
        val afterParent = InstallerSessionMachine.onParentExit(started, 2_000L)
        val evaluated = InstallerSessionMachine.evaluate(
            state = afterParent,
            nowMs = 2_000L + InstallerSessionMachine.QUIESCENCE_MS,
            discoveredExe = true,
            rebootHint = false,
        )
        assertEquals(InstallerSessionOutcome.QUIESCENT_SUCCESS, evaluated.outcome)
        assertNull(evaluated.reason)
    }

    @Test
    fun `child hang after parent exit needs review`() {
        var state = InstallerSessionState("s1", startedAtMs = 0L)
        state = InstallerSessionMachine.onChildSeen(state, 42, 100L)
        state = InstallerSessionMachine.onParentExit(state, 200L)
        val evaluated = InstallerSessionMachine.evaluate(
            state = state,
            nowMs = 200L + InstallerSessionMachine.CHILD_HANG_MS,
            discoveredExe = false,
            rebootHint = false,
        )
        assertEquals(InstallerReviewReason.CHILD_HANG, evaluated.reason)
    }

    @Test
    fun `timeout and reboot hints need review and cancel stays cancelled`() {
        val started = InstallerSessionState("s1", startedAtMs = 0L)
        val timedOut = InstallerSessionMachine.evaluate(
            started,
            InstallerSessionMachine.SESSION_TIMEOUT_MS,
            discoveredExe = false,
            rebootHint = false,
        )
        assertEquals(InstallerReviewReason.TIMEOUT, timedOut.reason)
        val reboot = InstallerSessionMachine.evaluate(started, 10L, discoveredExe = false, rebootHint = true)
        assertEquals(InstallerReviewReason.REBOOT_REQUIRED, reboot.reason)
        assertEquals(InstallerSessionOutcome.CANCELLED, InstallerSessionMachine.cancel(started).outcome)
    }
}
