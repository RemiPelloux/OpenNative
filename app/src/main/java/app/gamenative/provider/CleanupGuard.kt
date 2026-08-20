package app.gamenative.provider

data class CleanupDecision(
    val canDelete: Boolean,
    val reason: String = "",
)

object CleanupGuard {
    fun evaluate(
        policy: CleanupPolicy,
        sessionComplete: Boolean,
        requiredFilesPresent: Boolean,
        hashesMatch: Boolean,
        executableSelected: Boolean,
        receiptCommitted: Boolean,
        installerOwnedByJob: Boolean,
    ): CleanupDecision {
        if (policy == CleanupPolicy.KEEP) {
            return CleanupDecision(false, "Policy keeps the installer")
        }
        if (!sessionComplete) return CleanupDecision(false, "Install session is incomplete")
        if (!requiredFilesPresent) return CleanupDecision(false, "Required files are missing")
        if (!hashesMatch) return CleanupDecision(false, "Installer hash did not match")
        if (!executableSelected) return CleanupDecision(false, "No executable was selected")
        if (!receiptCommitted) return CleanupDecision(false, "Install receipt is missing")
        if (!installerOwnedByJob) return CleanupDecision(false, "Installer is not owned by this job")
        return CleanupDecision(true)
    }
}
