package app.gamenative.provider

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object SkidrowHtmlParser {
    fun looksLike(body: String, contentType: String?): Boolean {
        val type = contentType.orEmpty().lowercase()
        val htmlType = type.contains("html") ||
            body.trimStart().startsWith("<!doctype", ignoreCase = true) ||
            body.trimStart().startsWith("<html", ignoreCase = true)
        return htmlType && body.contains("skidrowreloaded", ignoreCase = true)
    }

    fun parse(body: String): ProviderFeedPage {
        val items = LinkedHashMap<String, ProviderFeedItem>()
        POST.findAll(body).forEachIndexed { index, match ->
            val link = match.groupValues[1]
            val slug = match.groupValues[2]
            if (slug.isBlank() || '/' in slug) return@forEachIndexed
            val title = HtmlText.plain(match.groupValues[3])
            if (title.isBlank() || CatalogFilter.isUpdateDigest(title, link, slug)) return@forEachIndexed
            val excerpt = body.substring(
                match.range.last + 1,
                minOf(body.length, match.range.last + 2_500),
            )
            val postedAt = parsePosted(excerpt)
            items.putIfAbsent(
                slug,
                ProviderFeedItem(
                    itemId = slug,
                    title = title,
                    link = link,
                    artworkUrl = IMAGE.find(excerpt)?.groupValues?.get(1),
                    publishedAtEpochMs = if (postedAt > 0L) postedAt - index else 0L,
                ),
            )
        }
        if (items.isEmpty()) {
            throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Skidrow listing is empty")
        }
        return ProviderFeedPage(items.values.toList())
    }

    private fun parsePosted(excerpt: String): Long {
        val raw = POSTED.find(excerpt)?.groupValues?.get(1) ?: return 0L
        return runCatching {
            val format = SimpleDateFormat("MMMM d, yyyy", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(raw)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private val POST = Regex(
        """<h2>\s*<a href="(https://www\.skidrowreloaded\.com/([^"?#]+)/)"[^>]*>(.*?)</a>\s*</h2>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val IMAGE = Regex(
        """<img[^>]+src=["'](https://[^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val POSTED = Regex("""Posted\s+([A-Za-z]+\s+\d{1,2},\s+\d{4})""", RegexOption.IGNORE_CASE)
}
