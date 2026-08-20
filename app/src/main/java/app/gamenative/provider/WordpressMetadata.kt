package app.gamenative.provider

import org.json.JSONArray
import org.json.JSONObject

object WordpressMetadata {
    private val SIZE = Regex(
        """(repack|original|download)\s*size\s*[:\-]?\s*([0-9]+(?:\.[0-9]+)?)(?:\s*/\s*([0-9]+(?:\.[0-9]+)?))?\s*(tib|tb|gib|gb|mib|mb|kib|kb)""",
        RegexOption.IGNORE_CASE,
    )
    private val HREF = Regex("""https://[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    fun sizes(text: String): Pair<Long, Long> {
        var download = 0L
        var uncompressed = 0L
        SIZE.findAll(HtmlText.plain(text)).forEach { match ->
            val label = match.groupValues[1].lowercase()
            val value = bytes(match)
            if (label == "original") uncompressed = value else if (download == 0L) download = value
        }
        return download to uncompressed
    }

    fun httpsLinks(html: String): List<String> =
        rankLinks(
            HREF.findAll(html).map { it.value.trimEnd('.', ',', ')', ']') }.toList(),
        )

    fun rankLinks(urls: List<String>, allowedHosts: Set<String>? = null): List<String> =
        WordpressDownloadLinks.rank(urls, allowedHosts)

    fun extraJson(links: List<String>, magnet: String = ""): String {
        if (links.isEmpty() && magnet.isBlank()) return "{}"
        val root = JSONObject()
        if (links.isNotEmpty()) {
            val array = JSONArray()
            links.forEach { array.put(it) }
            root.put("downloadLinks", array)
        }
        if (magnet.isNotBlank()) root.put("magnet", magnet)
        return root.toString()
    }

    fun magnetOf(item: ProviderFeedItem): String {
        val stored = magnetFrom(item.extraJson)
        if (stored.isNotBlank()) return stored
        return WordpressMagnets.first(item.description)
    }

    fun magnetFrom(extraJson: String): String {
        val root = runCatching { JSONObject(extraJson) }.getOrNull() ?: return ""
        return root.optString("magnet")
    }

    fun linksFrom(extraJson: String): List<String> {
        val root = runCatching { JSONObject(extraJson) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("downloadLinks") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val link = array.optString(index)
                if (link.isNotBlank()) add(link)
            }
        }.let { rankLinks(it) }
    }

    fun restrictForFeed(item: ProviderFeedItem, feedUrl: String): ProviderFeedItem {
        if (HosterAllowlist.hostsFor(feedUrl) == null) return item
        val links = HosterAllowlist.filter(linksFrom(item.extraJson), feedUrl)
        return item.copy(extraJson = extraJson(links))
    }

    fun candidateLinks(item: ProviderFeedItem): List<String> {
        val stored = linksFrom(item.extraJson)
        if (stored.isNotEmpty()) return stored
        return rankLinks(listOf(item.link))
    }

    fun preferredLink(item: ProviderFeedItem): String =
        candidateLinks(item).firstOrNull().orEmpty()

    private fun bytes(match: MatchResult): Long {
        val first = amount(match.groupValues[2], match.groupValues[4])
        val second = amount(match.groupValues[3], match.groupValues[4])
        return maxOf(first, second)
    }

    private fun amount(raw: String, unit: String): Long {
        val value = raw.toDoubleOrNull() ?: return 0L
        val multiplier = when (unit.lowercase()) {
            "tib", "tb" -> 1_099_511_627_776.0
            "gib", "gb" -> 1_073_741_824.0
            "mib", "mb" -> 1_048_576.0
            "kib", "kb" -> 1_024.0
            else -> 1.0
        }
        return (value * multiplier).toLong()
    }
}
