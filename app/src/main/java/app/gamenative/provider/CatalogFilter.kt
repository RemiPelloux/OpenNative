package app.gamenative.provider

object CatalogFilter {
    private val DIGEST_MARKERS = listOf(
        "updates digest",
        "update digest",
        "updates-digest",
        "update-digest",
    )

    fun isUpdateDigest(title: String, link: String = "", itemId: String = ""): Boolean {
        val haystack = "$title $link $itemId".lowercase()
        return DIGEST_MARKERS.any { marker -> haystack.contains(marker) }
    }

    fun isUpdateDigest(item: ProviderFeedItem): Boolean =
        isUpdateDigest(item.title, item.link, item.itemId)

    fun withoutNoise(items: List<ProviderFeedItem>): List<ProviderFeedItem> =
        items.filterNot { isUpdateDigest(it) }

    fun filter(items: List<ProviderFeedItem>, query: String): List<ProviderFeedItem> {
        val visible = withoutNoise(items)
        val needle = query.trim()
        if (needle.isEmpty()) return visible
        return visible.filter { item -> matches(item, needle) }
    }

    private fun matches(item: ProviderFeedItem, needle: String): Boolean {
        return item.title.contains(needle, ignoreCase = true) ||
            item.description.contains(needle, ignoreCase = true) ||
            item.architecture.contains(needle, ignoreCase = true)
    }
}
