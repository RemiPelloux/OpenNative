package app.gamenative.provider

data class DebridAccountState(
    val valid: Boolean,
    val username: String = "",
)

typealias AllDebridAccountState = DebridAccountState

data class ResolvedDownload(
    val filename: String,
    val url: String,
    val sizeBytes: Long = 0L,
)
