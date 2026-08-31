package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupAndStorageTest {
    @Test
    fun `cleanup requires every gate`() {
        val denied = CleanupGuard.evaluate(
            policy = CleanupPolicy.DELETE_AFTER_VERIFIED_INSTALL,
            sessionComplete = true,
            requiredFilesPresent = true,
            hashesMatch = true,
            executableSelected = false,
            receiptCommitted = true,
            installerOwnedByJob = true,
        )
        assertFalse(denied.canDelete)

        val allowed = CleanupGuard.evaluate(
            policy = CleanupPolicy.DELETE_AFTER_VERIFIED_INSTALL,
            sessionComplete = true,
            requiredFilesPresent = true,
            hashesMatch = true,
            executableSelected = true,
            receiptCommitted = true,
            installerOwnedByJob = true,
        )
        assertTrue(allowed.canDelete)
    }

    @Test
    fun `ask policy waits for confirmation`() {
        val waiting = CleanupGuard.evaluate(
            policy = CleanupPolicy.ASK,
            sessionComplete = true,
            requiredFilesPresent = true,
            hashesMatch = true,
            executableSelected = true,
            receiptCommitted = true,
            installerOwnedByJob = true,
            userConfirmed = false,
        )
        assertFalse(waiting.canDelete)
        val confirmed = CleanupGuard.evaluate(
            policy = CleanupPolicy.ASK,
            sessionComplete = true,
            requiredFilesPresent = true,
            hashesMatch = true,
            executableSelected = true,
            receiptCommitted = true,
            installerOwnedByJob = true,
            userConfirmed = true,
        )
        assertTrue(confirmed.canDelete)
    }

    @Test
    fun `keep policy never deletes`() {
        val decision = CleanupGuard.evaluate(
            policy = CleanupPolicy.KEEP,
            sessionComplete = true,
            requiredFilesPresent = true,
            hashesMatch = true,
            executableSelected = true,
            receiptCommitted = true,
            installerOwnedByJob = true,
        )
        assertFalse(decision.canDelete)
    }

    @Test
    fun `reserves installer extract and wine headroom`() {
        val required = StorageReservation.requiredBytes(100, 200, includeWineHeadroom = true)
        assertEquals(100 + 200 + StorageReservation.WINE_PREFIX_HEADROOM_BYTES, required)
        assertFalse(StorageReservation.hasSpace(50, required))
        assertTrue(StorageReservation.hasSpace(required, required))
    }

    @Test
    fun `state machine follows the documented path`() {
        assertTrue(TransferStateMachine.canTransition(TransferState.IDLE, TransferState.RESOLVING))
        assertTrue(TransferStateMachine.canTransition(TransferState.DOWNLOADING, TransferState.FAILED))
        assertFalse(TransferStateMachine.canTransition(TransferState.READY, TransferState.DOWNLOADING))
    }
}
