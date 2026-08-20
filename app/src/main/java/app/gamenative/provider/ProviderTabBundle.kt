package app.gamenative.provider

import org.json.JSONArray
import org.json.JSONObject

data class ProviderTabBundle(
    val schema: String,
    val exportedAtEpochMs: Long,
    val tabs: List<ProviderTab>,
) {
    companion object {
        const val SCHEMA = "opennative.provider.tabs/v1"
        const val GLOBAL_CREDENTIAL_REF = "global_alldebrid"
    }
}

object ProviderTabCodec {
    fun encode(tabs: List<ProviderTab>, nowMs: Long = System.currentTimeMillis()): String {
        val root = JSONObject()
        root.put("schema", ProviderTabBundle.SCHEMA)
        root.put("exportedAtEpochMs", nowMs)
        val array = JSONArray()
        tabs.forEach { tab -> array.put(encodeTab(tab)) }
        root.put("tabs", array)
        return root.toString(2)
    }

    fun decode(raw: String): List<ProviderTab> {
        val root = runCatching { JSONObject(raw) }.getOrElse {
            throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Tab bundle JSON is malformed")
        }
        val schema = root.optString("schema")
        if (schema.isNotBlank() && schema != ProviderTabBundle.SCHEMA) {
            throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Unsupported tab bundle schema")
        }
        val array = root.optJSONArray("tabs")
            ?: throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Tab bundle is missing tabs")
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                add(decodeTab(obj))
            }
        }
    }

    private fun encodeTab(tab: ProviderTab): JSONObject = JSONObject()
        .put("name", tab.name)
        .put("feedUrl", tab.feedUrl)
        .put("feedKind", tab.feedKind.name)
        .put("perPage", tab.perPage)
        .put("orderBy", tab.orderBy)
        .put("order", tab.order)
        .put("refreshPolicy", tab.refreshPolicy.name)
        .put("cleanupPolicy", tab.cleanupPolicy.name)
        .put("enabled", tab.enabled)

    private fun decodeTab(obj: JSONObject): ProviderTab {
        val name = obj.optString("name").trim()
        val feedUrl = FeedPaginator.canonicalFeedUrl(obj.optString("feedUrl"))
        if (name.isBlank() || feedUrl.isBlank()) {
            throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Each tab needs a name and feed URL")
        }
        ProviderUrlPolicy.validate(feedUrl).getOrThrow()
        return ProviderTab(
            id = "",
            name = name,
            position = 0,
            enabled = obj.optBoolean("enabled", true),
            feedUrl = feedUrl,
            feedKind = FeedKind.fromStored(obj.optString("feedKind")),
            perPage = obj.optInt("perPage", ProviderUrlPolicy.PAGE_SIZE).coerceIn(1, ProviderUrlPolicy.PAGE_SIZE),
            orderBy = obj.optString("orderBy").ifBlank { "date" },
            order = obj.optString("order").ifBlank { "desc" },
            refreshPolicy = RefreshPolicy.fromStored(obj.optString("refreshPolicy")),
            cleanupPolicy = CleanupPolicy.fromStored(obj.optString("cleanupPolicy")),
        )
    }
}
