package app.gamenative.provider

object ProviderFeedParser {
    fun parse(body: String, contentType: String? = null, kindHint: FeedKind? = null): ProviderFeedPage {
        if (SkidrowHtmlParser.looksLike(body, contentType)) {
            return limit(SkidrowHtmlParser.parse(body))
        }
        val kind = kindHint ?: FeedKind.detect(contentType, body)
        val page = when (kind) {
            FeedKind.JSON -> JsonFeedParser.parse(body)
            FeedKind.RSS -> RssFeedParser.parse(body)
        }
        return limit(page)
    }

    private fun limit(page: ProviderFeedPage): ProviderFeedPage {
        if (page.items.size <= ProviderUrlPolicy.PAGE_SIZE) return page
        return page.copy(items = page.items.take(ProviderUrlPolicy.PAGE_SIZE))
    }
}
