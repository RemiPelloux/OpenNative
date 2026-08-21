package app.gamenative.provider

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import org.json.JSONObject

class AllDebridClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val baseUrl: String = "https://api.alldebrid.com",
    private val allowLoopbackHttp: Boolean = false,
    private val delayedPollMs: Long = 5_000L,
    private val delayedAttempts: Int = 18,
    private val magnetPollMs: Long = 3_000L,
    private val magnetAttempts: Int = 200,
) : AllDebridResolver {
    private val http = AllDebridHttp(httpClient, baseUrl)
    private val magnets = AllDebridMagnetApi(http, magnetPollMs, magnetAttempts)

    override suspend fun validateCredential(apiKey: String): AllDebridAccountState {
        http.requireKey(apiKey)
        val json = http.get("/v4/user", apiKey)
        if (json.optString("status") != "success") {
            throw http.mapError(json, ProviderErrorCode.AUTHENTICATION)
        }
        val data = json.optJSONObject("data")?.optJSONObject("user")
        return AllDebridAccountState(
            valid = true,
            username = data?.optString("username").orEmpty(),
        )
    }

    override suspend fun resolve(
        apiKey: String,
        userSelectedLink: String,
        password: String,
    ): ResolvedDownload = resolve(apiKey, userSelectedLink, password, allowRedirector = true)

    override suspend fun uploadMagnet(apiKey: String, magnet: String): MagnetUpload =
        magnets.upload(apiKey, magnet)

    override suspend fun waitMagnetReady(apiKey: String, magnetId: Int) =
        magnets.waitReady(apiKey, magnetId)

    override suspend fun magnetFiles(apiKey: String, magnetId: Int): List<MagnetRemoteFile> =
        magnets.files(apiKey, magnetId)

    private suspend fun resolve(
        apiKey: String,
        userSelectedLink: String,
        password: String,
        allowRedirector: Boolean,
    ): ResolvedDownload {
        http.requireKey(apiKey)
        ProviderUrlPolicy.validate(userSelectedLink, allowLoopbackHttp).getOrThrow()
        val query = buildMap {
            put("link", userSelectedLink)
            if (password.isNotBlank()) put("password", password)
        }
        val json = http.get("/v4/link/unlock", apiKey, query)
        if (json.optString("status") == "success") return fromUnlock(apiKey, json)
        val error = http.mapError(json, ProviderErrorCode.UNAVAILABLE_LINK)
        if (allowRedirector && error.code == ProviderErrorCode.UNSUPPORTED_HOST) {
            val nested = redirectorLinks(apiKey, userSelectedLink)
            if (nested.isNotEmpty()) {
                return resolve(apiKey, nested.first(), password, allowRedirector = false)
            }
        }
        throw error
    }

    private suspend fun fromUnlock(apiKey: String, json: JSONObject): ResolvedDownload {
        val data = json.optJSONObject("data")
            ?: throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Unlock payload is malformed")
        val filename = data.optString("filename").ifBlank { "download.bin" }
        val url = data.optString("link")
        val delayedId = data.optInt("delayed", 0)
        if (url.isBlank() && delayedId > 0) {
            return pollDelayed(apiKey, delayedId, filename, data.optLong("filesize"))
        }
        if (url.isBlank()) {
            throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Unlock did not return a file")
        }
        return ResolvedDownload(filename = filename, url = url, sizeBytes = data.optLong("filesize"))
    }

    private suspend fun pollDelayed(
        apiKey: String,
        delayedId: Int,
        filename: String,
        sizeBytes: Long,
    ): ResolvedDownload {
        repeat(delayedAttempts) {
            delay(delayedPollMs)
            val json = http.get("/v4/link/delayed", apiKey, mapOf("id" to delayedId.toString()))
            if (json.optString("status") != "success") {
                throw http.mapError(json, ProviderErrorCode.UNAVAILABLE_LINK)
            }
            val data = json.optJSONObject("data") ?: return@repeat
            when (data.optInt("status")) {
                2 -> {
                    val url = data.optString("link")
                    if (url.isBlank()) {
                        throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Delayed unlock returned no file")
                    }
                    return ResolvedDownload(filename = filename, url = url, sizeBytes = sizeBytes)
                }
                3 -> throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Delayed unlock failed")
            }
        }
        throw ProviderException(ProviderErrorCode.TIMEOUT, "Delayed unlock timed out")
    }

    private fun redirectorLinks(apiKey: String, userSelectedLink: String): List<String> {
        val json = runCatching {
            http.get("/v4/link/redirector", apiKey, mapOf("link" to userSelectedLink))
        }.getOrNull() ?: return emptyList()
        if (json.optString("status") != "success") return emptyList()
        val array = json.optJSONObject("data")?.optJSONArray("links") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val link = array.optString(index)
                if (link.startsWith("https://")) add(link)
            }
        }.take(8)
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
