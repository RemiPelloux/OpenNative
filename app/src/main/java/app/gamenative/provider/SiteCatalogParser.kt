package app.gamenative.provider

import org.json.JSONObject

data class SiteCatalogSpec(
    val id: String,
    val marker: String,
    val host: String,
    val itemLink: Regex,
    val nextPage: Regex? = null,
)

object SiteCatalogParser {
    private val ITEM = Regex(
        """<h2[^>]*>\s*<a href="(https://([^"/]+)/([^"?#]+)/?)"[^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val IMAGE = Regex(
        """<img[^>]+src=["'](https://[^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val SIZE = Regex(
        """(?:size|download)\s*[:\-]\s*([0-9]+(?:[.,][0-9]+)?)\s*(KB|MB|GB)""",
        RegexOption.IGNORE_CASE,
    )
    private val PASSWORD = Regex(
        """password\s*[:\-]\s*([A-Za-z0-9._-]{3,32})""",
        RegexOption.IGNORE_CASE,
    )

    val STEAMRIP = SiteCatalogSpec(
        id = "steamrip",
        marker = "steamrip",
        host = "steamrip.com",
        itemLink = ITEM,
        nextPage = Regex("""href="(https://steamrip\.com/page/\d+/?)"""", RegexOption.IGNORE_CASE),
    )
    val ANKERGAMES = SiteCatalogSpec(
        id = "ankergames",
        marker = "ankergames",
        host = "ankergames.net",
        itemLink = ITEM,
        nextPage = Regex("""href="(https://ankergames\.net/page/\d+/?)"""", RegexOption.IGNORE_CASE),
    )
    val GOGUNLOCKED = SiteCatalogSpec(
        id = "gogunlocked",
        marker = "gogunlocked",
        host = "gogunlocked.com",
        itemLink = ITEM,
        nextPage = Regex("""href="(https://gogunlocked\.com/page/\d+/?)"""", RegexOption.IGNORE_CASE),
    )

    val ALL = listOf(STEAMRIP, ANKERGAMES, GOGUNLOCKED)

    fun matchingSpec(body: String, contentType: String?): SiteCatalogSpec? {
        if (!looksLikeHtml(body, contentType)) return null
        return ALL.firstOrNull { spec -> body.contains(spec.marker, ignoreCase = true) }
    }

    fun parse(body: String, spec: SiteCatalogSpec): ProviderFeedPage {
        val items = LinkedHashMap<String, ProviderFeedItem>()
        spec.itemLink.findAll(body).forEach { match ->
            val link = match.groupValues[1]
            if (!link.contains(spec.host, ignoreCase = true)) return@forEach
            val slug = match.groupValues[3].trim('/').substringAfterLast('/')
            if (slug.isBlank() || slug == "page") return@forEach
            val title = HtmlText.plain(match.groupValues[4])
            if (title.isBlank() || CatalogFilter.isUpdateDigest(title, link, slug)) return@forEach
            val excerpt = excerptAfter(body, match.range.last)
            items.putIfAbsent(slug, item(slug, title, link, excerpt))
        }
        if (items.isEmpty()) {
            throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "${spec.id} listing is empty")
        }
        return ProviderFeedPage(
            items = items.values.toList(),
            nextCursor = spec.nextPage?.find(body)?.groupValues?.get(1),
        )
    }

    private fun item(slug: String, title: String, link: String, excerpt: String): ProviderFeedItem {
        val password = PASSWORD.find(excerpt)?.groupValues?.get(1).orEmpty()
        val extra = if (password.isBlank()) "{}" else JSONObject().put("archivePassword", password).toString()
        return ProviderFeedItem(
            itemId = slug,
            title = title,
            link = link,
            artworkUrl = IMAGE.find(excerpt)?.groupValues?.get(1),
            downloadSizeBytes = parseSize(excerpt),
            description = HtmlText.plain(excerpt).take(280),
            extraJson = extra,
        )
    }

    private fun excerptAfter(body: String, start: Int): String =
        body.substring(start, minOf(body.length, start + 2_500))

    private fun looksLikeHtml(body: String, contentType: String?): Boolean {
        val type = contentType.orEmpty().lowercase()
        val trimmed = body.trimStart()
        return type.contains("html") ||
            trimmed.startsWith("<!doctype", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true)
    }

    private fun parseSize(excerpt: String): Long {
        val match = SIZE.find(excerpt) ?: return 0L
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
        val unit = match.groupValues[2].uppercase()
        val multiplier = when (unit) {
            "KB" -> 1_024.0
            "MB" -> 1_024.0 * 1_024.0
            "GB" -> 1_024.0 * 1_024.0 * 1_024.0
            else -> 1.0
        }
        return (amount * multiplier).toLong()
    }
}
