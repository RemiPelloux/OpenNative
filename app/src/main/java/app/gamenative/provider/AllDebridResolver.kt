package app.gamenative.provider

interface DebridResolver {
    val provider: DebridProvider
    suspend fun validateCredential(apiKey: String): DebridAccountState
    suspend fun resolve(apiKey: String, userSelectedLink: String, password: String = ""): ResolvedDownload
}

interface MagnetDebridResolver : DebridResolver {
    suspend fun uploadMagnet(apiKey: String, magnet: String): MagnetUpload
    suspend fun waitMagnetReady(apiKey: String, magnetId: String)
    suspend fun magnetFiles(apiKey: String, magnetId: String): List<MagnetRemoteFile>
}

interface AllDebridResolver : MagnetDebridResolver {
    override val provider: DebridProvider
        get() = DebridProvider.ALL_DEBRID
}
