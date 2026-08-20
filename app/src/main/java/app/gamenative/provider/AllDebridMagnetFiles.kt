package app.gamenative.provider

import org.json.JSONArray
import org.json.JSONObject

data class MagnetRemoteFile(
    val relativePath: String,
    val link: String,
    val sizeBytes: Long = 0L,
)

object AllDebridMagnetFiles {
    fun flatten(nodes: JSONArray, prefix: String = ""): List<MagnetRemoteFile> = buildList {
        for (index in 0 until nodes.length()) {
            val node = nodes.optJSONObject(index) ?: continue
            addAll(flattenNode(node, prefix))
        }
    }

    private fun flattenNode(node: JSONObject, prefix: String): List<MagnetRemoteFile> {
        val name = node.optString("n").ifBlank { node.optString("name") }
        val path = if (prefix.isBlank()) name else "$prefix/$name"
        val children = node.optJSONArray("e")
        if (children != null) return flatten(children, path)
        val link = node.optString("l").ifBlank { node.optString("link") }
        if (!link.startsWith("https://")) return emptyList()
        return listOf(
            MagnetRemoteFile(
                relativePath = path.ifBlank { "download.bin" },
                link = link,
                sizeBytes = node.optLong("s").takeIf { it > 0L } ?: node.optLong("size"),
            ),
        )
    }
}
