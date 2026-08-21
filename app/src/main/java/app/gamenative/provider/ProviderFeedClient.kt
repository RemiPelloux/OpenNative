package app.gamenative.provider

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

class ProviderFeedClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val allowLoopbackHttp: Boolean = false,
    private val parser: (String, String?, FeedKind?) -> ProviderFeedPage = ProviderFeedParser::parse,
) {
    suspend fun fetch(
        url: String,
        cursor: String? = null,
        etag: String? = null,
        lastModified: String? = null,
        kindHint: FeedKind? = null,
        page: Int = 1,
        perPage: Int = ProviderUrlPolicy.PAGE_SIZE,
        orderBy: String = "date",
        order: String = "desc",
        search: String = "",
    ): ProviderFeedPage {
        val style = FeedPaginator.detectStyle(url, kindHint ?: FeedKind.JSON)
        val pagedUrl = FeedPaginator.apply(
            rawUrl = url,
            request = FeedPageRequest(page, perPage, orderBy, order, cursor, search),
            style = style,
        )
        val uri = ProviderUrlPolicy.validate(pagedUrl, allowLoopbackHttp).getOrThrow()
        val request = Request.Builder().url(uri.toString()).get().apply {
            header("User-Agent", USER_AGENT)
            if (!etag.isNullOrBlank()) header("If-None-Match", etag)
            if (!lastModified.isNullOrBlank()) header("If-Modified-Since", lastModified)
        }.build()
        return execute(request, kindHint, page)
    }

    fun fetchText(url: String): String {
        val uri = ProviderUrlPolicy.validate(url, allowLoopbackHttp).getOrThrow()
        val request = Request.Builder().url(uri.toString()).get()
            .header("User-Agent", USER_AGENT)
            .build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: IOException) {
            throw ProviderException(ProviderErrorCode.NETWORK, "Feed request failed")
        }
        return response.use { resp ->
            if (!resp.isSuccessful) throw mapHttp(resp.code)
            val body = resp.body?.string().orEmpty()
            body.take(ProviderUrlPolicy.MAX_RESPONSE_BYTES)
        }
    }

    private fun execute(request: Request, kindHint: FeedKind?, page: Int): ProviderFeedPage {
        val response = try {
            httpClient.newCall(request).execute()
        } catch (io: IOException) {
            throw ProviderException(ProviderErrorCode.NETWORK, "Feed request failed")
        }
        response.use { resp ->
            if (resp.code == 304) {
                return ProviderFeedPage(items = emptyList(), notModified = true)
            }
            if (resp.code == 404 && page > 1) {
                return ProviderFeedPage(items = emptyList(), page = page)
            }
            if (!resp.isSuccessful) {
                throw mapHttp(resp.code)
            }
            val body = resp.body?.bytes() ?: ByteArray(0)
            if (body.size > ProviderUrlPolicy.MAX_RESPONSE_BYTES) {
                throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Feed response is too large")
            }
            val parsed = parser(String(body, Charsets.UTF_8), resp.header("Content-Type"), kindHint)
            return parsed.copy(
                etag = resp.header("ETag"),
                lastModified = resp.header("Last-Modified"),
                page = page,
                totalPages = resp.header("X-WP-TotalPages")?.toIntOrNull(),
            )
        }
    }

    private fun mapHttp(code: Int): ProviderException = when (code) {
        401, 403 -> ProviderException(ProviderErrorCode.AUTHENTICATION, "Feed authentication failed")
        429 -> ProviderException(ProviderErrorCode.RATE_LIMIT, "Feed rate limited")
        else -> ProviderException(ProviderErrorCode.NETWORK, "Feed request failed")
    }

    companion object {
        private const val USER_AGENT = "OpenNative/1.3.0 (Android)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
