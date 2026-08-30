package app.gamenative.provider

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private class DebridJsonHttp(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    fun get(path: String, token: String, query: Map<String, String> = emptyMap()): JSONObject =
        execute(path, token, query, null)

    fun post(path: String, token: String, fields: List<Pair<String, String>>): JSONObject =
        execute(path, token, emptyMap(), fields)

    fun postJson(path: String, token: String, fields: List<Pair<String, String>>): JSONObject =
        executeJson(path, token, fields)

    private fun execute(
        path: String,
        token: String,
        query: Map<String, String>,
        fields: List<Pair<String, String>>?,
    ): JSONObject {
        val key = token.trim()
        if (key.isBlank()) throw ProviderException(ProviderErrorCode.AUTHENTICATION, "Resolver credential is missing")
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments(path.trimStart('/')).apply {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val builder = Request.Builder().url(url).header("Authorization", "Bearer $key")
        if (fields == null) {
            builder.get()
        } else {
            builder.post(FormBody.Builder().apply { fields.forEach { (name, value) -> add(name, value) } }.build())
        }
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (error: IOException) {
            throw ProviderException(ProviderErrorCode.NETWORK, "Resolver request failed", error)
        }
        response.use {
            if (it.code == 401 || it.code == 403) {
                throw ProviderException(ProviderErrorCode.AUTHENTICATION, "Resolver authentication failed")
            }
            if (it.code == 429) throw ProviderException(ProviderErrorCode.RATE_LIMIT, "Resolver rate limited")
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Resolver request failed (${it.code})")
            if (body.isBlank()) return JSONObject()
            return runCatching { JSONObject(body) }.getOrElse {
                throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Resolver response is malformed")
            }
        }
    }

    private fun executeJson(path: String, token: String, fields: List<Pair<String, String>>): JSONObject {
        val key = token.trim()
        if (key.isBlank()) throw ProviderException(ProviderErrorCode.AUTHENTICATION, "Resolver credential is missing")
        val body = JSONObject().apply { fields.forEach { (name, value) -> put(name, value) } }.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(baseUrl.toHttpUrl().newBuilder().addPathSegments(path.trimStart('/')).build())
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw ProviderException(ProviderErrorCode.NETWORK, "Resolver request failed", error)
        }
        response.use {
            if (it.code == 401 || it.code == 403) {
                throw ProviderException(ProviderErrorCode.AUTHENTICATION, "Resolver authentication failed")
            }
            if (it.code == 429) throw ProviderException(ProviderErrorCode.RATE_LIMIT, "Resolver rate limited")
            val responseBody = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Resolver request failed (${it.code})")
            return runCatching { JSONObject(responseBody) }.getOrElse {
                throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Resolver response is malformed")
            }
        }
    }
}

private fun validateLink(value: String) {
    ProviderUrlPolicy.validate(value).getOrThrow()
}

private fun JSONObject.requireSuccess(
    fallback: String = "Resolver request failed",
    predicate: JSONObject.() -> Boolean,
) {
    if (!predicate()) {
        val detail = ProviderUrlPolicy.redact(optString("error").ifBlank { optString("detail") }).trim()
        val auth = detail.contains("token", true) || detail.contains("auth", true)
        throw ProviderException(
            if (auth) ProviderErrorCode.AUTHENTICATION else ProviderErrorCode.UNAVAILABLE_LINK,
            if (auth) "Resolver authentication failed" else detail.ifBlank { fallback },
        )
    }
}

class RealDebridClient(
    client: OkHttpClient = defaultDebridClient(),
    baseUrl: String = "https://api.real-debrid.com/rest/1.0",
) : MagnetDebridResolver {
    override val provider = DebridProvider.REAL_DEBRID
    private val http = DebridJsonHttp(client, baseUrl)

    override suspend fun validateCredential(apiKey: String): DebridAccountState {
        val json = http.get("/user", apiKey)
        val username = json.optString("username")
        if (username.isBlank() && json.optLong("id") <= 0L) {
            throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Resolver account response is malformed")
        }
        return DebridAccountState(true, username)
    }

    override suspend fun resolve(apiKey: String, userSelectedLink: String, password: String): ResolvedDownload {
        validateLink(userSelectedLink)
        val json = http.post(
            "/unrestrict/link",
            apiKey,
            buildList {
                add("link" to userSelectedLink)
                if (password.isNotBlank()) add("password" to password)
            },
        )
        val url = json.optString("download")
        if (url.isBlank()) throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Unlock did not return a file")
        return ResolvedDownload(json.optString("filename").ifBlank { "download.bin" }, url, json.optLong("filesize"))
    }

    override suspend fun uploadMagnet(apiKey: String, magnet: String): MagnetUpload {
        if (!magnet.startsWith("magnet:?", ignoreCase = true)) {
            throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "No magnet link in this post")
        }
        val added = http.post("/torrents/addMagnet", apiKey, listOf("magnet" to magnet))
        val id = added.optString("id")
        if (id.isBlank()) throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Magnet upload is malformed")
        http.post("/torrents/selectFiles/$id", apiKey, listOf("files" to "all"))
        return MagnetUpload(id = id, ready = false)
    }

    override suspend fun waitMagnetReady(apiKey: String, magnetId: String) {
        repeat(200) {
            val info = http.get("/torrents/info/$magnetId", apiKey)
            when (info.optString("status")) {
                "downloaded" -> return
                "magnet_error", "error", "virus", "dead" ->
                    throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Magnet processing failed")
            }
            delay(3_000L)
        }
        throw ProviderException(ProviderErrorCode.TIMEOUT, "Magnet processing timed out")
    }

    override suspend fun magnetFiles(apiKey: String, magnetId: String): List<MagnetRemoteFile> {
        val info = http.get("/torrents/info/$magnetId", apiKey)
        val files = info.optJSONArray("files")
            ?: throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Magnet file list is malformed")
        val links = info.optJSONArray("links")
            ?: throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Magnet links are missing")
        var linkIndex = 0
        return buildList {
            for (index in 0 until files.length()) {
                val file = files.optJSONObject(index) ?: continue
                if (file.optInt("selected", 1) != 1) continue
                val link = links.optString(linkIndex++)
                if (link.isBlank()) continue
                add(MagnetRemoteFile(file.optString("path").trimStart('/'), link, file.optLong("bytes")))
            }
        }
    }
}

class PremiumizeClient(
    client: OkHttpClient = defaultDebridClient(),
    baseUrl: String = "https://www.premiumize.me/api",
) : DebridResolver {
    override val provider = DebridProvider.PREMIUMIZE
    private val http = DebridJsonHttp(client, baseUrl)

    override suspend fun validateCredential(apiKey: String): DebridAccountState {
        val json = http.get("/account/info", apiKey)
        json.requireSuccess { optString("status") == "success" }
        val id = json.optString("customer_id")
        if (id.isBlank()) throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Resolver account response is malformed")
        return DebridAccountState(true, id)
    }

    override suspend fun resolve(apiKey: String, userSelectedLink: String, password: String): ResolvedDownload {
        validateLink(userSelectedLink)
        val json = http.post("/transfer/directdl", apiKey, listOf("src" to userSelectedLink))
        json.requireSuccess { optString("status") == "success" }
        val file = json.optJSONArray("content")?.optJSONObject(0)
            ?: throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Unlock did not return a file")
        val url = file.optString("link")
        if (url.isBlank()) throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Unlock did not return a file")
        return ResolvedDownload(file.optString("path").ifBlank { "download.bin" }, url, file.optLong("size"))
    }
}

class DebridLinkClient(
    client: OkHttpClient = defaultDebridClient(),
    baseUrl: String = "https://debrid-link.com/api/v2",
) : DebridResolver {
    override val provider = DebridProvider.DEBRID_LINK
    private val http = DebridJsonHttp(client, baseUrl)

    override suspend fun validateCredential(apiKey: String): DebridAccountState {
        val json = http.get("/account/infos", apiKey)
        json.requireSuccess { optBoolean("success") }
        val value = json.optJSONObject("value")
            ?: throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Resolver account response is malformed")
        return DebridAccountState(true, value.optString("username"))
    }

    override suspend fun resolve(apiKey: String, userSelectedLink: String, password: String): ResolvedDownload {
        validateLink(userSelectedLink)
        val json = http.postJson(
            "/downloader/add",
            apiKey,
            buildList {
                add("url" to userSelectedLink)
                if (password.isNotBlank()) add("password" to password)
            },
        )
        json.requireSuccess { optBoolean("success") }
        val value = json.optJSONObject("value")
            ?: throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Unlock payload is malformed")
        val url = value.optString("downloadUrl")
        if (url.isBlank()) throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Unlock did not return a file")
        return ResolvedDownload(value.optString("name").ifBlank { "download.bin" }, url, value.optLong("size"))
    }
}

class TorBoxClient(
    client: OkHttpClient = defaultDebridClient(),
    baseUrl: String = "https://api.torbox.app/v1/api",
    private val pollDelayMs: Long = 2_000L,
    private val pollAttempts: Int = 45,
) : DebridResolver {
    override val provider = DebridProvider.TORBOX
    private val http = DebridJsonHttp(client, baseUrl)

    override suspend fun validateCredential(apiKey: String): DebridAccountState {
        val json = http.get("/user/me", apiKey)
        json.requireSuccess { optBoolean("success") }
        val data = json.optJSONObject("data")
            ?: throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Resolver account response is malformed")
        return DebridAccountState(true, data.optString("email").ifBlank { data.optString("id") })
    }

    override suspend fun resolve(apiKey: String, userSelectedLink: String, password: String): ResolvedDownload {
        validateLink(userSelectedLink)
        val created = http.post(
            "/webdl/createwebdownload",
            apiKey,
            buildList {
                add("link" to userSelectedLink)
                if (password.isNotBlank()) add("password" to password)
            },
        )
        created.requireSuccess { optBoolean("success") }
        val id = created.optJSONObject("data")?.optLong("webdownload_id", -1L) ?: -1L
        if (id < 0L) throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Unlock payload is malformed")
        repeat(pollAttempts) {
            val listing = http.get("/webdl/mylist", apiKey, mapOf("id" to id.toString(), "bypass_cache" to "true"))
            listing.requireSuccess { optBoolean("success") }
            val item = listing.optJSONArray("data")?.optJSONObject(0)
            if (item != null && item.optBoolean("download_finished")) {
                val file = item.optJSONArray("files")?.optJSONObject(0)
                    ?: throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Unlock did not return a file")
                val fileId = file.optLong("id", -1L)
                val unlocked = http.get(
                    "/webdl/requestdl",
                    apiKey,
                    mapOf("token" to apiKey.trim(), "web_id" to id.toString(), "file_id" to fileId.toString()),
                )
                unlocked.requireSuccess { optBoolean("success") }
                val url = unlocked.optString("data")
                if (url.isBlank()) throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Unlock did not return a file")
                return ResolvedDownload(file.optString("name").ifBlank { "download.bin" }, url, file.optLong("size"))
            }
            delay(pollDelayMs)
        }
        throw ProviderException(ProviderErrorCode.TIMEOUT, "TorBox web download timed out")
    }
}

private fun defaultDebridClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .build()
