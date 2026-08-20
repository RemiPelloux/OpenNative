package app.gamenative.provider

import org.json.JSONArray
import org.json.JSONObject

/**
 * Generic WordPress REST post list. Maps public title, link, excerpt and cover media.
 * File-hoster HTTPS links are stored in extraJson; magnets and blog pages are ignored.
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
        val title = HtmlText.decode(rendered(obj, "title").ifBlank { obj.optString("slug") })
        val link = obj.optString("link")
        val id = obj.opt("id")?.toString().orEmpty().ifBlank { link }
        if (title.isBlank() || link.isBlank()) return null
        if (CatalogFilter.isUpdateDigest(title, link, id)) return null
        val excerpt = rendered(obj, "excerpt")
        val content = rendered(obj, "content")
        val (download, uncompressed) = WordpressMetadata.sizes("$excerpt $content")
        return ProviderFeedItem(
            itemId = id,
            title = title,
            description = HtmlText.plain(excerpt),
            link = link,
            artworkUrl = WordpressArtwork.from(obj),
            downloadSizeBytes = download,
            uncompressedSizeBytes = uncompressed,
            publishedAtEpochMs = 0L,
            extraJson = WordpressMetadata.extraJson(WordpressMetadata.httpsLinks(content)),
        )
    }

    private fun rendered(obj: JSONObject, key: String): String {
        val value = obj.opt(key)
        if (value is JSONObject) return value.optString("rendered")
        return obj.optString(key)
    }
}
