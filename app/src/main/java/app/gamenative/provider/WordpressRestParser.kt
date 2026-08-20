package app.gamenative.provider

import org.json.JSONArray
import org.json.JSONObject

/**
 * Generic WordPress REST post list. Maps public title/link/media only.
 * It does not read post HTML or extract download links from content.
 */
object WordpressRestParser {
    fun looksLike(body: String): Boolean {
        val trimmed = body.trimStart()
        if (!trimmed.startsWith("[")) return false
        val first = runCatching { JSONArray(body).optJSONObject(0) }.getOrNull() ?: return false
        return first.has("title") || first.has("link") || first.has("slug")
    }

    fun parse(body: String): ProviderFeedPage {
        val array = runCatching { JSONArray(body) }.getOrElse {
            throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "WordPress feed JSON is malformed")
        }
        val items = ArrayList<ProviderFeedItem>(array.length())
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            parseItem(obj)?.let { items += it }
        }
        return ProviderFeedPage(items = items)
    }

    private fun parseItem(obj: JSONObject): ProviderFeedItem? {
        val title = rendered(obj, "title").ifBlank { obj.optString("slug") }
        val link = obj.optString("link")
        val id = obj.opt("id")?.toString().orEmpty().ifBlank { link }
        if (title.isBlank() || link.isBlank()) return null
        return ProviderFeedItem(
            itemId = id,
            title = title,
            description = strip(rendered(obj, "excerpt")),
            link = link,
            artworkUrl = artwork(obj),
            publishedAtEpochMs = 0L,
            extraJson = "{}",
        )
    }

    private fun rendered(obj: JSONObject, key: String): String {
        val value = obj.opt(key)
        if (value is JSONObject) return value.optString("rendered")
        return obj.optString(key)
    }

    private fun artwork(obj: JSONObject): String? {
        val jetpack = obj.optString("jetpack_featured_media_url")
        if (jetpack.isNotBlank()) return jetpack
        val embedded = obj.optJSONObject("_embedded") ?: return null
        val media = embedded.optJSONArray("wp:featuredmedia")?.optJSONObject(0)
        return media?.optString("source_url")?.ifBlank { null }
    }

    private fun strip(html: String): String =
        html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
}
