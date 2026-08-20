package app.gamenative.provider

import java.net.URI

object HosterAllowlist {
    private val MEGA = setOf("mega.nz", "mega.io", "mega.co.nz")

    fun hostsFor(feedUrl: String): Set<String>? {
        val lower = feedUrl.lowercase()
        if (lower.contains("skidrow")) return MEGA
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
