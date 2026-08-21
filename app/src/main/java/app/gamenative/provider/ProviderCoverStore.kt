package app.gamenative.provider

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

object ProviderCoverStore {
    fun save(
        artworkUrl: String?,
        dest: File,
        httpClient: OkHttpClient = defaultClient(),
    ): File? {
        val url = artworkUrl?.trim().orEmpty()
        if (url.isBlank()) return null
        ProviderUrlPolicy.validate(url).getOrElse { return null }
        dest.mkdirs()
        val out = File(dest, "cover.${extension(url)}")
        val request = Request.Builder().url(url).get()
            .header("User-Agent", "OpenNative/1.3.0 (Android)")
            .build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: IOException) {
            return null
        }
        return response.use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            if (bytes.isEmpty() || bytes.size > ProviderUrlPolicy.MAX_ARTWORK_BYTES) return null
            out.writeBytes(bytes)
            out.takeIf { it.isFile && it.length() > 0L }
        }
    }

    internal fun extension(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".png") -> "png"
            path.endsWith(".webp") -> "webp"
            else -> "jpg"
        }
    }

    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}
