package app.gamenative.provider

object HosterPassword {
    private val LABELED = Regex(
        """(?:file\s+)?password\s*[:\-]\s*([A-Za-z0-9._@+\-]{3,64})""",
        RegexOption.IGNORE_CASE,
    )

    fun fromHtml(html: String): String {
        if (html.isBlank()) return ""
        return LABELED.find(HtmlText.plain(html))?.groupValues?.get(1).orEmpty()
    }
}
