package app.gamenative.provider

import java.io.File

object ProviderPathSlug {
    fun slug(raw: String): String {
        val cleaned = raw.lowercase()
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return cleaned.ifBlank { "game" }.take(72)
    }

    fun legacy(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._ -]"), "").trim().ifBlank { "game" }.take(72)

    fun slugDirectories(relativePath: String): String {
        val parts = relativePath.replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return ""
        if (parts.size == 1) return parts[0]
        val dirs = parts.dropLast(1).joinToString("/") { slugDir(it) }
        return "$dirs/${parts.last()}"
    }

    private fun slugDir(name: String): String {
        val key = name.lowercase()
        if (key == "md5" || key == "_commonredist") return name
        return slug(name)
    }

    fun resolveFolder(title: String, root: File): File {
        val slugged = File(root, slug(title))
        if (slugged.isDirectory) return slugged
        val old = File(root, legacy(title))
        if (old.isDirectory) return old
        return slugged
    }

    fun ensureFolder(title: String, root: File): File {
        val slugged = File(root, slug(title))
        val old = File(root, legacy(title))
        if (old.isDirectory && old.absolutePath != slugged.absolutePath && !slugged.exists()) {
            if (old.renameTo(slugged)) return slugged
            return old
        }
        slugged.mkdirs()
        return slugged
    }
}
