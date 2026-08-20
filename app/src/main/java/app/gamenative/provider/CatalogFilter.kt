package app.gamenative.provider

object CatalogFilter {
    fun filter(items: List<ProviderFeedItem>, query: String): List<ProviderFeedItem> {
        val needle = query.trim()
        if (needle.isEmpty()) return items
        return items.filter { item -> matches(item, needle) }
    }

    private fun matches(item: ProviderFeedItem, needle: String): Boolean {
        return item.title.contains(needle, ignoreCase = true) ||
            item.description.contains(needle, ignoreCase = true) ||
            item.architecture.contains(needle, ignoreCase = true)
    }
}
