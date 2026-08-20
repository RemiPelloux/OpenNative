package app.gamenative.provider

interface AllDebridResolver {
    suspend fun validateCredential(apiKey: String): AllDebridAccountState
    suspend fun resolve(apiKey: String, userSelectedLink: String): ResolvedDownload
    suspend fun uploadMagnet(apiKey: String, magnet: String): MagnetUpload
    suspend fun waitMagnetReady(apiKey: String, magnetId: Int)
    suspend fun magnetFiles(apiKey: String, magnetId: Int): List<MagnetRemoteFile>
}
