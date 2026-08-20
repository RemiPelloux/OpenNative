package app.gamenative.provider

import org.json.JSONArray
import org.json.JSONObject

object JsonFeedParser {
    fun parse(body: String): ProviderFeedPage {
        if (WordpressRestParser.looksLike(body)) {
            return WordpressRestParser.parse(body)
        }
        val root = runCatching { JSONObject(body) }.getOrElse {
            throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Feed JSON is malformed")
        }
        val version = root.optInt("version", 0)
        if (version != 1) {
            throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Unsupported feed version")
        }
        val items = parseItems(root.optJSONArray("items") ?: JSONArray())
        return ProviderFeedPage(
            items = items,
            nextCursor = root.optString("nextCursor").ifBlank { null },
        )
    }

    private fun parseItems(array: JSONArray): List<ProviderFeedItem> {
        val items = ArrayList<ProviderFeedItem>(array.length())
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val item = parseItem(obj) ?: continue
            items += item
        }
        return items
    }

    private fun parseItem(obj: JSONObject): ProviderFeedItem? {
        val id = obj.optString("id").ifBlank { obj.optString("itemId") }
        val title = HtmlText.decode(obj.optString("title"))
        val link = obj.optString("link")
        if (id.isBlank() || title.isBlank() || link.isBlank()) return null
        if (CatalogFilter.isUpdateDigest(title, link, id)) return null
        return ProviderFeedItem(
            itemId = id,
            title = title,
            version = obj.optString("version"),
            architecture = obj.optString("architecture"),
            downloadSizeBytes = obj.optLong("size", obj.optLong("downloadSizeBytes")),
            uncompressedSizeBytes = obj.optLong("uncompressedSizeBytes"),
            sha256 = obj.optString("sha256").ifBlank { null },
            artworkUrl = obj.optString("artworkUrl").ifBlank { null },
            description = HtmlText.plain(obj.optString("description")),
            link = link,
            profileRef = obj.optString("profileRef").ifBlank { null },
            publishedAtEpochMs = obj.optLong("publishedAt"),
            extraJson = obj.toString(),
        )
    }
}
