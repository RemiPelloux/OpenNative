package app.gamenative.provider

object ProviderFeedTarget {
    fun resolve(feedUrl: String): String {
        if (feedUrl.contains("skidrow", ignoreCase = true)) {
            return "https://www.skidrowreloaded.com/"
        }
        return feedUrl
    }

    fun kindHint(feedUrl: String, stored: FeedKind): FeedKind {
        if (feedUrl.contains("skidrow", ignoreCase = true)) return FeedKind.RSS
        val resolved = resolve(feedUrl)
        return if (resolved.contains("/wp-json/")) FeedKind.JSON else stored
    }
}
