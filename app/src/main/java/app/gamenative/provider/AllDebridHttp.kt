package app.gamenative.provider

import java.io.IOException
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AllDebridHttp(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
) {
    fun get(path: String, apiKey: String, query: Map<String, String> = emptyMap()): JSONObject =
        execute(path, apiKey, query, fields = null)

    fun post(path: String, apiKey: String, fields: List<Pair<String, String>>): JSONObject =
        execute(path, apiKey, emptyMap(), fields)

    fun requireKey(apiKey: String) {
        if (apiKey.isBlank()) {
            throw ProviderException(ProviderErrorCode.AUTHENTICATION, "Resolver credential is missing")
        }
    }

    fun mapError(json: JSONObject, fallback: ProviderErrorCode): ProviderException {
        val error = json.optJSONObject("error")
        val code = error?.optString("code").orEmpty()
        val mapped = when (code) {
            "AUTH_BAD_APIKEY", "AUTH_MISSING_APIKEY", "MAGNET_MUST_BE_PREMIUM" ->
                ProviderErrorCode.AUTHENTICATION
            "LINK_HOST_NOT_SUPPORTED" -> ProviderErrorCode.UNSUPPORTED_HOST
            "LINK_DOWN", "LINK_ERROR", "MAGNET_INVALID_URI", "MAGNET_NO_URI" ->
                ProviderErrorCode.UNAVAILABLE_LINK
            "MAGNET_TOO_MANY_ACTIVE" -> ProviderErrorCode.RATE_LIMIT
            else -> fallback
        }
        return ProviderException(mapped, resolverMessage(mapped, error?.optString("message").orEmpty()))
    }

    private fun execute(
        path: String,
        apiKey: String,
        query: Map<String, String>,
        fields: List<Pair<String, String>>?,
    ): JSONObject {
        val builder = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(path.trimStart('/'))
            .addQueryParameter("agent", "OpenNative")
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder()
            .url(builder.build())
            .header("Authorization", "Bearer $apiKey")
        if (fields != null) {
            val form = FormBody.Builder()
            fields.forEach { (key, value) -> form.add(key, value) }
            request.post(form.build())
        } else {
            request.get()
        }
        return read(request.build())
    }

    private fun read(request: Request): JSONObject {
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

    private fun resolverMessage(code: ProviderErrorCode, detail: String): String {
        val safe = ProviderUrlPolicy.redact(detail).trim()
        return when {
            code == ProviderErrorCode.AUTHENTICATION -> "Resolver authentication failed"
            code == ProviderErrorCode.UNSUPPORTED_HOST -> "This file host is not supported"
            safe.isNotBlank() -> safe
            else -> "Resolver request failed"
        }
    }
}
