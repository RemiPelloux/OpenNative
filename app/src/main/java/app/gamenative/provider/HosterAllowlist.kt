package app.gamenative.provider

import java.net.URI

object HosterAllowlist {
    private val FICHIER = setOf("1fichier.com")

    fun hostsFor(feedUrl: String): Set<String>? {
        if (ProviderTabPolicy.extractOnly(feedUrl)) return FICHIER
        return null
    }

    fun filter(urls: List<String>, feedUrl: String): List<String> {
        val allowed = hostsFor(feedUrl) ?: return urls
        return urls.filter { url ->
            val host = runCatching { URI(url).host?.lowercase().orEmpty() }.getOrDefault("")
            allowed.any { host == it || host.endsWith(".$it") }
        }
    }
}
