package app.gamenative.provider

interface AllDebridResolver {
    suspend fun validateCredential(apiKey: String): AllDebridAccountState
    suspend fun resolve(apiKey: String, userSelectedLink: String): ResolvedDownload
}
