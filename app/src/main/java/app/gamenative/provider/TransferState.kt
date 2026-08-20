package app.gamenative.provider

enum class TransferState {
    IDLE,
    RESOLVING,
    QUEUED,
    DOWNLOADING,
    VERIFYING,
    INSTALLING,
    VERIFYING_INSTALL,
    CLEANING,
    READY,
    PAUSED,
    CANCELLED,
    FAILED,
    NEEDS_REVIEW,
}

object TransferStateMachine {
    private val active = setOf(
        TransferState.RESOLVING,
        TransferState.QUEUED,
        TransferState.DOWNLOADING,
        TransferState.VERIFYING,
        TransferState.INSTALLING,
        TransferState.VERIFYING_INSTALL,
        TransferState.CLEANING,
    )

    fun canTransition(from: TransferState, to: TransferState): Boolean {
        if (from == to) return true
        if (to == TransferState.PAUSED || to == TransferState.CANCELLED || to == TransferState.FAILED) {
            return from in active || from == TransferState.IDLE
        }
        return when (from) {
            TransferState.IDLE -> to == TransferState.RESOLVING
            TransferState.RESOLVING -> to == TransferState.QUEUED
            TransferState.QUEUED -> to == TransferState.DOWNLOADING
            TransferState.PAUSED -> to == TransferState.DOWNLOADING || to == TransferState.QUEUED
            TransferState.DOWNLOADING -> to == TransferState.VERIFYING
            TransferState.VERIFYING -> to == TransferState.INSTALLING
            TransferState.INSTALLING -> to == TransferState.VERIFYING_INSTALL || to == TransferState.NEEDS_REVIEW
            TransferState.VERIFYING_INSTALL -> to == TransferState.CLEANING || to == TransferState.READY
            TransferState.CLEANING -> to == TransferState.READY
            else -> false
        }
    }
}
