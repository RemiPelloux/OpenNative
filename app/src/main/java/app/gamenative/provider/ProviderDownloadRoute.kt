package app.gamenative.provider

object ProviderDownloadRoute {
    fun magnetFor(feedUrl: String, provided: String, scrape: () -> String = { "" }): String {
        if (HosterAllowlist.hostsFor(feedUrl) != null) return ""
        return provided.trim().ifBlank(scrape)
    }
}
