package app.gamenative.provider

object ProviderFeedParser {
    fun parse(body: String, contentType: String? = null, kindHint: FeedKind? = null): ProviderFeedPage {
        val kind = kindHint ?: FeedKind.detect(contentType, body)
        val page = when (kind) {
            FeedKind.JSON -> JsonFeedParser.parse(body)
            FeedKind.RSS -> RssFeedParser.parse(body)
        }
        if (page.items.size > ProviderUrlPolicy.PAGE_SIZE) {
            return page.copy(items = page.items.take(ProviderUrlPolicy.PAGE_SIZE))
        }
        return page
    }
}
