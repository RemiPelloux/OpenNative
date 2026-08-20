package app.gamenative.provider

/**
 * Turns WordPress HTML titles and excerpts into readable catalog text.
 * WordPress REST leaves entities such as `&#038;` in `title.rendered`.
 */
object HtmlText {
    private val TAGS = Regex("<[^>]+>")
    private val SPACE = Regex("\\s+")
    private val ENTITY = Regex("&(?:#x([0-9a-fA-F]+)|#([0-9]+)|([a-zA-Z]+));")
    private val NAMED = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "ndash" to "–",
        "mdash" to "—",
        "rsquo" to "’",
        "lsquo" to "‘",
        "rdquo" to "”",
        "ldquo" to "“",
        "hellip" to "…",
    )

    fun decode(text: String): String {
        var current = text
        repeat(3) {
            val next = current.replace(ENTITY) { entityValue(it) }
            if (next == current) return next
            current = next
        }
        return current
    }

    fun plain(html: String): String = decode(strip(html))

    fun strip(html: String): String =
        html.replace(TAGS, " ").replace(SPACE, " ").trim()

    private fun entityValue(match: MatchResult): String {
        val hex = match.groupValues[1]
        val decimal = match.groupValues[2]
        val name = match.groupValues[3]
        return when {
            hex.isNotEmpty() -> fromCode(hex.toIntOrNull(16)) ?: match.value
            decimal.isNotEmpty() -> fromCode(decimal.toIntOrNull()) ?: match.value
            else -> NAMED[name] ?: match.value
        }
    }

    private fun fromCode(value: Int?): String? {
        if (value == null || value !in 1..0x10FFFF || value in 0xD800..0xDFFF) return null
        return String(Character.toChars(value))
    }
}
