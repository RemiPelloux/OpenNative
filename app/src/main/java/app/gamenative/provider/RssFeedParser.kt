package app.gamenative.provider

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList

object RssFeedParser {
    fun parse(body: String): ProviderFeedPage {
        val factory = secureFactory()
        val document = runCatching {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)))
        }.getOrElse {
            throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Feed XML is malformed")
        }
        val root = document.documentElement ?: throw ProviderException(
            ProviderErrorCode.MALFORMED_RESPONSE,
            "Feed XML is empty",
        )
        val items = when (root.tagName.lowercase()) {
            "rss" -> parseRss(root)
            "feed" -> parseAtom(root)
            else -> throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Unsupported XML feed")
        }
        return ProviderFeedPage(items = items)
    }

    private fun parseRss(root: Element): List<ProviderFeedItem> {
        val nodes = root.getElementsByTagName("item")
        return mapNodes(nodes) { element ->
            val link = firstText(element, "link").ifBlank { enclosureUrl(element) }
            val title = firstText(element, "title")
            val id = firstText(element, "guid").ifBlank { link }
            if (title.isBlank() || link.isBlank()) return@mapNodes null
            ProviderFeedItem(
                itemId = id,
                title = title,
                description = stripTags(firstText(element, "description")),
                link = link,
                downloadSizeBytes = enclosureLength(element),
                artworkUrl = mediaUrl(element),
            )
        }
    }

    private fun parseAtom(root: Element): List<ProviderFeedItem> {
        val nodes = root.getElementsByTagName("entry")
        return mapNodes(nodes) { element ->
            val link = atomLink(element)
            val title = firstText(element, "title")
            val id = firstText(element, "id").ifBlank { link }
            if (title.isBlank() || link.isBlank()) return@mapNodes null
            ProviderFeedItem(
                itemId = id,
                title = title,
                description = stripTags(firstText(element, "summary").ifBlank { firstText(element, "content") }),
                link = link,
            )
        }
    }

    private fun mapNodes(
        nodes: NodeList,
        transform: (Element) -> ProviderFeedItem?,
    ): List<ProviderFeedItem> {
        val items = ArrayList<ProviderFeedItem>(nodes.length)
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) as? Element ?: continue
            transform(node)?.let { items += it }
        }
        return items
    }

    private fun firstText(parent: Element, tag: String): String {
        val list = parent.getElementsByTagName(tag)
        if (list.length == 0) return ""
        return list.item(0).textContent?.trim().orEmpty()
    }

    private fun enclosureUrl(item: Element): String {
        val list = item.getElementsByTagName("enclosure")
        if (list.length == 0) return ""
        return (list.item(0) as? Element)?.getAttribute("url").orEmpty()
    }

    private fun enclosureLength(item: Element): Long {
        val list = item.getElementsByTagName("enclosure")
        if (list.length == 0) return 0L
        return (list.item(0) as? Element)?.getAttribute("length")?.toLongOrNull() ?: 0L
    }

    private fun mediaUrl(item: Element): String? {
        val thumbs = item.getElementsByTagName("media:thumbnail")
        if (thumbs.length > 0) {
            val url = (thumbs.item(0) as? Element)?.getAttribute("url")
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    private fun atomLink(entry: Element): String {
        val list = entry.getElementsByTagName("link")
        for (index in 0 until list.length) {
            val element = list.item(index) as? Element ?: continue
            val rel = element.getAttribute("rel")
            val href = element.getAttribute("href")
            if (href.isNotBlank() && (rel.isBlank() || rel == "alternate" || rel == "enclosure")) {
                return href
            }
        }
        return firstText(entry, "link")
    }

    private fun stripTags(html: String): String = html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

    private fun secureFactory(): DocumentBuilderFactory {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isExpandEntityReferences = false
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        return factory
    }
}
