package app.gamenative.provider

object ProviderTabPolicy {
    fun extractOnly(feedUrl: String): Boolean =
        feedUrl.contains("skidrow", ignoreCase = true)
}
