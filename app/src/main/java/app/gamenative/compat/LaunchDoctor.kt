package app.gamenative.compat

enum class LaunchFailureKind {
    MISSING_COMPONENT,
    INVALID_EXECUTABLE,
    WINE_FAILURE,
    CPU_TRANSLATION,
    GRAPHICS_INIT,
    LOW_STORAGE,
    ABNORMAL_EXIT,
}

enum class LaunchRecovery {
    RETRY,
    SAFE_LAUNCH,
    REPAIR_COMPONENT,
    CHOOSE_EXECUTABLE,
    EXPORT_REPORT,
}

data class LaunchDiagnosis(
    val kind: LaunchFailureKind,
    val reason: String,
    val recovery: LaunchRecovery,
    val wine: String,
    val translator: String,
    val graphics: String,
    val dxWrapper: String,
    val resolution: String,
    val frameLimit: String,
    val timeline: List<String>,
)

data class LaunchDoctorInput(
    val hint: String = "",
    val executableMissing: Boolean = false,
    val executablePath: String = "",
    val wine: String = "",
    val translator: String = "",
    val graphics: String = "",
    val dxWrapper: String = "",
    val resolution: String = "",
    val frameLimit: String = "",
    val freeBytes: Long = Long.MAX_VALUE,
    val timeline: List<String> = emptyList(),
    val sessionLengthSec: Int = 0,
)

object LaunchDoctor {
    const val LOW_STORAGE_BYTES = 512L * 1024L * 1024L

    fun classify(input: LaunchDoctorInput): LaunchDiagnosis {
        val hint = input.hint.lowercase()
        val kind = kindOf(input, hint)
        return LaunchDiagnosis(
            kind = kind,
            reason = reasonOf(kind),
            recovery = recoveryOf(kind),
            wine = input.wine,
            translator = input.translator,
            graphics = input.graphics,
            dxWrapper = input.dxWrapper,
            resolution = input.resolution,
            frameLimit = input.frameLimit,
            timeline = input.timeline,
        )
    }

    fun summary(diagnosis: LaunchDiagnosis): String = buildString {
        append(diagnosis.reason)
        append(" Active stack: Wine/Proton ")
        append(diagnosis.wine.ifBlank { "unknown" })
        append(", translator ")
        append(diagnosis.translator.ifBlank { "unknown" })
        append(", graphics ")
        append(diagnosis.graphics.ifBlank { "unknown" })
        append(", DirectX ")
        append(diagnosis.dxWrapper.ifBlank { "unknown" })
        append('.')
        if (diagnosis.timeline.isNotEmpty()) {
            append(" Stages: ")
            append(diagnosis.timeline.joinToString(" → "))
            append('.')
        }
    }

    private fun kindOf(input: LaunchDoctorInput, hint: String): LaunchFailureKind = when {
        input.freeBytes < LOW_STORAGE_BYTES || hint.contains("no space") -> LaunchFailureKind.LOW_STORAGE
        input.executableMissing || hint.contains("not found") || hint.contains("invalid exe") ->
            LaunchFailureKind.INVALID_EXECUTABLE
        hint.contains("missing") && (hint.contains("component") || hint.contains("dxvk") || hint.contains("wine")) ->
            LaunchFailureKind.MISSING_COMPONENT
        hint.contains("box64") || hint.contains("fex") || hint.contains("sigill") || hint.contains("illegal instruction") ->
            LaunchFailureKind.CPU_TRANSLATION
        hint.contains("vkcreate") || hint.contains("vulkan") || hint.contains("adreno") || hint.contains("egl") ->
            LaunchFailureKind.GRAPHICS_INIT
        hint.contains("wine") || hint.contains("wineserver") -> LaunchFailureKind.WINE_FAILURE
        else -> LaunchFailureKind.ABNORMAL_EXIT
    }

    private fun reasonOf(kind: LaunchFailureKind): String = when (kind) {
        LaunchFailureKind.MISSING_COMPONENT -> "A required runtime component is missing."
        LaunchFailureKind.INVALID_EXECUTABLE -> "The selected executable is missing or is not a game binary."
        LaunchFailureKind.WINE_FAILURE -> "Wine failed to start or exited before the game window appeared."
        LaunchFailureKind.CPU_TRANSLATION -> "CPU translation faulted while starting the Windows binary."
        LaunchFailureKind.GRAPHICS_INIT -> "Graphics initialization failed before the first frame."
        LaunchFailureKind.LOW_STORAGE -> "There is not enough free storage to complete this launch."
        LaunchFailureKind.ABNORMAL_EXIT -> "The session ended before a playable state was reached."
    }

    private fun recoveryOf(kind: LaunchFailureKind): LaunchRecovery = when (kind) {
        LaunchFailureKind.MISSING_COMPONENT -> LaunchRecovery.REPAIR_COMPONENT
        LaunchFailureKind.INVALID_EXECUTABLE -> LaunchRecovery.CHOOSE_EXECUTABLE
        LaunchFailureKind.LOW_STORAGE -> LaunchRecovery.EXPORT_REPORT
        LaunchFailureKind.WINE_FAILURE,
        LaunchFailureKind.CPU_TRANSLATION,
        LaunchFailureKind.GRAPHICS_INIT,
        -> LaunchRecovery.SAFE_LAUNCH
        LaunchFailureKind.ABNORMAL_EXIT -> LaunchRecovery.RETRY
    }
}
