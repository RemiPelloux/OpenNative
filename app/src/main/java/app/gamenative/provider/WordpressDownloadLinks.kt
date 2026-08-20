package app.gamenative.provider

import java.net.URI

object WordpressDownloadLinks {
    private val IMAGE_EXT = Regex(
        """\.(?:jpe?g|png|webp|gif|svg|ico|webm)(?:\?|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val ARCHIVE_EXT = Regex(
        """\.(?:rar|zip|7z|exe|bin|iso)(?:[#?].*)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val HOSTERS = setOf(
        "datanodes.to",
        "fuckingfast.co",
        "filekeeper.net",
        "gofile.io",
        "pixeldrain.com",
        "mega.nz",
        "mega.io",
        "mega.co.nz",
        "mediafire.com",
        "1fichier.com",
        "megaup.net",
        "krakenfiles.com",
        "buzzheavier.com",
        "send.cm",
        "rapidgator.net",
        "nitroflare.com",
        "keep2share.cc",
        "k2s.cc",
        "multiup.io",
        "multiup.org",
        "pixeldrain.net",
        "workupload.com",
    )
    private val NOISE_HOSTS = setOf(
        "fitgirl-repacks.site",
        "skidrowreloaded.com",
        "1337x.to",
        "rutor.info",
        "tapochek.net",
        "cs.rin.ru",
        "riotpixels.com",
        "steamstatic.com",
        "internetdownloadmanager.com",
        "youtube.com",
        "youtu.be",
        "x.com",
        "twitter.com",
    )

    fun rank(urls: List<String>, allowedHosts: Set<String>? = null): List<String> =
        urls.filter { isDownloadLink(it) }
            .filter { allowedHosts == null || hostAllowed(it, allowedHosts) }
            .distinct()
            .sortedByDescending { score(it) }
            .take(12)

    private fun hostAllowed(url: String, allowed: Set<String>): Boolean {
        val host = hostOf(url)
        return allowed.any { matchesHost(host, it) }
    }

    fun isDownloadLink(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        if (url.contains("magnet:", ignoreCase = true)) return false
        if (IMAGE_EXT.containsMatchIn(url)) return false
        if (score(url) <= 0) return false
        return ProviderUrlPolicy.validate(url).isSuccess
    }

    private fun score(url: String): Int {
        val host = hostOf(url)
        if (host.isBlank() || isNoise(host)) return 0
        val path = url.lowercase()
        var value = 0
        if (isHoster(host)) value += 100
        if (ARCHIVE_EXT.containsMatchIn(path)) value += 50
        if (path.contains(".part1.")) value += 20
        if (path.contains("optional") || path.contains("soundtrack")) value -= 30
        return value
    }

    private fun hostOf(url: String): String =
        runCatching { URI(url).host?.lowercase().orEmpty() }.getOrDefault("")

    private fun isHoster(host: String): Boolean = HOSTERS.any { matchesHost(host, it) }

    private fun isNoise(host: String): Boolean = NOISE_HOSTS.any { matchesHost(host, it) }

    private fun matchesHost(host: String, listed: String): Boolean =
        host == listed || host.endsWith(".$listed")
}
