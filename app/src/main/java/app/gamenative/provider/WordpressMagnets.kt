package app.gamenative.provider

object WordpressMagnets {
    private val MAGNET = Regex("""magnet:\?[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    fun first(html: String): String =
        MAGNET.findAll(html)
            .map { HtmlText.decode(it.value.trimEnd('.', ',', ')', ']')) }
            .firstOrNull { it.startsWith("magnet:?", ignoreCase = true) }
            .orEmpty()
}
