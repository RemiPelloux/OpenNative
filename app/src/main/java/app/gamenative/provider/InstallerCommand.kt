package app.gamenative.provider

data class InstallerLaunchPlan(
    val executable: String,
    val arguments: List<String>,
    val workingDirectory: String,
)

object InstallerCommand {
    fun plan(kind: PayloadKind, installerPath: String, workingDirectory: String): InstallerLaunchPlan {
        return when (kind) {
            PayloadKind.WINDOWS_EXE -> InstallerLaunchPlan(
                executable = installerPath,
                arguments = emptyList(),
                workingDirectory = workingDirectory,
            )
            PayloadKind.WINDOWS_MSI -> InstallerLaunchPlan(
                executable = "msiexec",
                arguments = listOf("/i", installerPath),
                workingDirectory = workingDirectory,
            )
            PayloadKind.PORTABLE_ARCHIVE, PayloadKind.UNKNOWN ->
                throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Payload is not an installer")
        }
    }
}
