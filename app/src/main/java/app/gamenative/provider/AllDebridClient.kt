package app.gamenative.provider

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AllDebridClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val baseUrl: String = "https://api.alldebrid.com",
    private val allowLoopbackHttp: Boolean = false,
) : AllDebridResolver {
    override suspend fun validateCredential(apiKey: String): AllDebridAccountState {
        requireKey(apiKey)
        val json = get("/v4/user", apiKey)
        val status = json.optString("status")
        if (status != "success") {
            throw mapError(json, ProviderErrorCode.AUTHENTICATION)
        }
        val data = json.optJSONObject("data")?.optJSONObject("user")
        return AllDebridAccountState(
            valid = true,
            username = data?.optString("username").orEmpty(),
        )
    }

    override suspend fun resolve(apiKey: String, userSelectedLink: String): ResolvedDownload {
        requireKey(apiKey)
        ProviderUrlPolicy.validate(userSelectedLink, allowLoopbackHttp).getOrThrow()
        val json = get("/v4/link/unlock", apiKey, mapOf("link" to userSelectedLink))
        if (json.optString("status") != "success") {
            throw mapError(json, ProviderErrorCode.UNAVAILABLE_LINK)
        }
        val data = json.optJSONObject("data")
            ?: throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Unlock payload is malformed")
        val filename = data.optString("filename").ifBlank { "download.bin" }
        val url = data.optString("link")
        if (url.isBlank()) {
            throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Unlock did not return a file")
        }
        return ResolvedDownload(
            filename = filename,
            url = url,
            sizeBytes = data.optLong("filesize"),
        )
    }

    private fun get(path: String, apiKey: String, query: Map<String, String> = emptyMap()): JSONObject {
        val builder = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(path.trimStart('/'))
            .addQueryParameter("agent", "OpenNative")
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder()
            .url(builder.build())
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: IOException) {
            throw ProviderException(ProviderErrorCode.NETWORK, "Link resolver request failed")
        }
        response.use { resp ->
            if (resp.code == 429) throw ProviderException(ProviderErrorCode.RATE_LIMIT, "Resolver rate limited")
            if (resp.code == 401 || resp.code == 403) {
                throw ProviderException(ProviderErrorCode.AUTHENTICATION, "Resolver authentication failed")
            }
            val body = resp.body?.string().orEmpty()
            return runCatching { JSONObject(body) }.getOrElse {
                throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Resolver response is malformed")
            }
        }
    }

    private fun requireKey(apiKey: String) {
        if (apiKey.isBlank()) {
            throw ProviderException(ProviderErrorCode.AUTHENTICATION, "Resolver credential is missing")
        }
    }

    private fun mapError(json: JSONObject, fallback: ProviderErrorCode): ProviderException {
        val code = json.optJSONObject("error")?.optString("code").orEmpty()
        val mapped = when (code) {
            "AUTH_BAD_APIKEY", "AUTH_MISSING_APIKEY" -> ProviderErrorCode.AUTHENTICATION
            "LINK_HOST_NOT_SUPPORTED" -> ProviderErrorCode.UNSUPPORTED_HOST
            "LINK_DOWN", "LINK_ERROR" -> ProviderErrorCode.UNAVAILABLE_LINK
            else -> fallback
        }
        return ProviderException(mapped, "Resolver request failed")
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
