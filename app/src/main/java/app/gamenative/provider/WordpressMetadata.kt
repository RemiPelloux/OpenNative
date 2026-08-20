package app.gamenative.provider

import org.json.JSONArray
import org.json.JSONObject

object WordpressMetadata {
    private val SIZE = Regex(
        """(repack|original|download)\s*size\s*[:\-]?\s*([0-9]+(?:\.[0-9]+)?)\s*(tib|tb|gib|gb|mib|mb|kib|kb)""",
        RegexOption.IGNORE_CASE,
    )
    private val HREF = Regex("""https://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val IMAGE_EXT = Regex("""\.(?:jpe?g|png|webp|gif|svg|ico)(?:\?|$)""", RegexOption.IGNORE_CASE)

    fun sizes(text: String): Pair<Long, Long> {
        var download = 0L
        var uncompressed = 0L
        SIZE.findAll(text).forEach { match ->
            val label = match.groupValues[1].lowercase()
            val value = bytes(match)
            if (label == "original") uncompressed = value else if (download == 0L) download = value
        }
        return download to uncompressed
    }

    fun httpsLinks(html: String): List<String> =
        HREF.findAll(html)
            .map { it.value.trimEnd('.', ',', ')', ']') }
            .filter { isDownloadLink(it) }
            .distinct()
            .take(8)
            .toList()

    fun extraJson(links: List<String>): String {
        if (links.isEmpty()) return "{}"
        val root = JSONObject()
        val array = JSONArray()
        links.forEach { array.put(it) }
        root.put("downloadLinks", array)
        return root.toString()
    }

    fun linksFrom(extraJson: String): List<String> {
        val root = runCatching { JSONObject(extraJson) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("downloadLinks") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val link = array.optString(index)
                if (isDownloadLink(link)) add(link)
            }
        }
    }

    fun preferredLink(item: ProviderFeedItem): String =
        linksFrom(item.extraJson).firstOrNull() ?: item.link

    private fun isDownloadLink(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        if (url.contains("magnet:", ignoreCase = true)) return false
        if (IMAGE_EXT.containsMatchIn(url)) return false
        val host = runCatching { java.net.URI(url).host?.lowercase().orEmpty() }.getOrDefault("")
        if (host.isBlank()) return false
        if (host.endsWith("imageban.ru") || host.endsWith("tenor.com") || host.endsWith("gravatar.com")) {
            return false
        }
        return ProviderUrlPolicy.validate(url).isSuccess
    }

    private fun bytes(match: MatchResult?): Long {
        if (match == null) return 0L
        val amount = match.groupValues[2].toDoubleOrNull() ?: return 0L
        val unit = match.groupValues[3].lowercase()
        val multiplier = when (unit) {
            "tib", "tb" -> 1_099_511_627_776.0
            "gib", "gb" -> 1_073_741_824.0
            "mib", "mb" -> 1_048_576.0
            "kib", "kb" -> 1_024.0
            else -> 1.0
        }
        return (amount * multiplier).toLong()
    }
}
