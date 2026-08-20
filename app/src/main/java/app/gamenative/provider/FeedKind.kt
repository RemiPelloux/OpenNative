package app.gamenative.provider

enum class FeedKind {
    JSON,
    RSS,
    ;

    companion object {
        fun fromStored(value: String): FeedKind =
            entries.find { it.name == value } ?: JSON

        fun detect(contentType: String?, body: String): FeedKind {
            val type = contentType.orEmpty().lowercase()
            if (type.contains("json")) return JSON
            if (type.contains("xml") || type.contains("rss") || type.contains("atom")) return RSS
            val trimmed = body.trimStart()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) return JSON
            return RSS
        }
    }
}
