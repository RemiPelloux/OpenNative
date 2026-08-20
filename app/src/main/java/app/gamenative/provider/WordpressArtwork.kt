package app.gamenative.provider

import org.json.JSONObject

object WordpressArtwork {
    private val HTTPS_IMAGE = Regex(
        """https://[^\s"'<>]+\.(?:jpe?g|png|webp)(?:\?[^\s"'<>]*)?""",
        RegexOption.IGNORE_CASE,
    )

    fun from(obj: JSONObject): String? {
        val jetpack = obj.optString("jetpack_featured_media_url")
        if (isHttpsImage(jetpack)) return jetpack
        embedded(obj)?.let { return it }
        yoast(obj)?.let { return it }
        return firstImage(rendered(obj, "excerpt"))
            ?: firstImage(rendered(obj, "content"))
    }

    private fun embedded(obj: JSONObject): String? {
        val media = obj.optJSONObject("_embedded")
            ?.optJSONArray("wp:featuredmedia")
            ?.optJSONObject(0)
        val url = media?.optString("source_url").orEmpty()
        return url.takeIf(::isHttpsImage)
    }

    private fun yoast(obj: JSONObject): String? {
        val image = obj.optJSONObject("yoast_head_json")
            ?.optJSONArray("og_image")
            ?.optJSONObject(0)
        val url = image?.optString("url").orEmpty()
        return url.takeIf(::isHttpsImage)
    }

    private fun rendered(obj: JSONObject, key: String): String {
        val value = obj.opt(key)
        if (value is JSONObject) return value.optString("rendered")
        return obj.optString(key)
    }

    private fun firstImage(html: String): String? =
        HTTPS_IMAGE.find(html)?.value?.takeIf(::isHttpsImage)

    private fun isHttpsImage(url: String): Boolean {
        if (url.isBlank()) return false
        return ProviderUrlPolicy.validate(url).isSuccess
    }
}
