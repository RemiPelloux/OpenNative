package app.gamenative.provider

data class FeedPageRequest(
    val page: Int,
    val perPage: Int = ProviderUrlPolicy.PAGE_SIZE,
    val orderBy: String = "date",
    val order: String = "desc",
    val cursor: String? = null,
    val search: String = "",
)

enum class PaginationStyle {
    CURSOR,
    PAGE,
    WORDPRESS_REST,
    WORDPRESS_RSS,
}

object FeedPaginator {
    fun detectStyle(url: String, kind: FeedKind): PaginationStyle {
        val lower = url.lowercase()
        if (lower.contains("/wp-json/") || lower.contains("per_page=")) {
            return PaginationStyle.WORDPRESS_REST
        }
        if (kind == FeedKind.RSS || lower.contains("/feed") || lower.contains("paged=")) {
            return PaginationStyle.WORDPRESS_RSS
        }
        if (lower.contains("cursor=")) return PaginationStyle.CURSOR
        return PaginationStyle.PAGE
    }

    fun apply(rawUrl: String, request: FeedPageRequest, style: PaginationStyle): String {
        val stripped = stripPaging(rawUrl)
        val params = linkedMapOf<String, String>()
        when (style) {
            PaginationStyle.CURSOR -> {
                if (!request.cursor.isNullOrBlank()) params["cursor"] = request.cursor
            }
            PaginationStyle.PAGE -> {
                params["page"] = request.page.toString()
                params["per_page"] = request.perPage.coerceIn(1, ProviderUrlPolicy.PAGE_SIZE).toString()
            }
            PaginationStyle.WORDPRESS_REST -> {
                params["page"] = request.page.toString()
                params["per_page"] = request.perPage.coerceIn(1, ProviderUrlPolicy.PAGE_SIZE).toString()
                params["orderby"] = sanitizeOrderBy(request.orderBy)
                params["order"] = sanitizeOrder(request.order)
                params["_fields"] = WP_REST_FIELDS
            }
            PaginationStyle.WORDPRESS_RSS -> {
                params["paged"] = request.page.toString()
                params["page"] = request.page.toString()
                params["orderby"] = sanitizeOrderBy(request.orderBy)
                params["order"] = sanitizeOrder(request.order)
            }
        }
        applySearch(params, request.search, style)
        return appendQuery(stripped, params)
    }

    private fun applySearch(
        params: MutableMap<String, String>,
        search: String,
        style: PaginationStyle,
    ) {
        val query = search.trim()
        if (query.isEmpty()) return
        val encoded = encode(query)
        when (style) {
            PaginationStyle.WORDPRESS_REST, PaginationStyle.PAGE, PaginationStyle.CURSOR ->
                params["search"] = encoded
            PaginationStyle.WORDPRESS_RSS -> params["s"] = encoded
        }
    }

    fun hasMore(fetchedPage: Int, itemCount: Int, perPage: Int, totalPages: Int?, nextCursor: String?): Boolean {
        if (!nextCursor.isNullOrBlank()) return true
        if (totalPages != null) return fetchedPage < totalPages
        return itemCount >= perPage
    }

    internal fun stripPaging(rawUrl: String): String {
        val qIndex = rawUrl.indexOf('?')
        if (qIndex < 0) return rawUrl
        val base = rawUrl.substring(0, qIndex)
        val kept = rawUrl.substring(qIndex + 1).split('&').filter { part ->
            val key = part.substringBefore('=').lowercase()
            key !in PAGING_KEYS
        }
        return if (kept.isEmpty()) base else "$base?${kept.joinToString("&")}"
    }

    private fun appendQuery(url: String, params: Map<String, String>): String {
        if (params.isEmpty()) return url
        val separator = if (url.contains('?')) "&" else "?"
        val encoded = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return url + separator + encoded
    }

    private fun sanitizeOrderBy(value: String): String {
        val cleaned = value.trim().lowercase().ifBlank { "date" }
        return if (cleaned in ORDER_BY) cleaned else "date"
    }

    private fun sanitizeOrder(value: String): String {
        val cleaned = value.trim().lowercase()
        return if (cleaned == "asc" || cleaned == "desc") cleaned else "desc"
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun canonicalFeedUrl(rawUrl: String): String = stripPaging(rawUrl.trim())

    private val PAGING_KEYS = setOf(
        "page", "per_page", "paged", "orderby", "order", "cursor", "search", "s",
        "_fields", "_embed", "embed",
    )
    private const val WP_REST_FIELDS =
        "id,slug,link,title,excerpt,content,jetpack_featured_media_url,featured_media"
    private val ORDER_BY = setOf("date", "modified", "title", "id", "relevance")
}
