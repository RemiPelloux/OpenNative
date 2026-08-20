package app.gamenative.provider

data class AllDebridAccountState(
    val valid: Boolean,
    val username: String = "",
)

data class ResolvedDownload(
    val filename: String,
    val url: String,
    val sizeBytes: Long = 0L,
)
