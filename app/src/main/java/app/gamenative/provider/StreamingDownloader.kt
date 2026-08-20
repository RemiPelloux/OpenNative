package app.gamenative.provider

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import okhttp3.OkHttpClient
import okhttp3.Request

class StreamingDownloader(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val bufferSize: Int = StreamingHasher.BUFFER_BYTES,
) {
    fun download(
        url: String,
        partialFile: File,
        expectedBytes: Long = 0L,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): File {
        partialFile.parentFile?.mkdirs()
        val existing = if (partialFile.exists()) partialFile.length() else 0L
        val request = Request.Builder().url(url).get().apply {
            if (existing > 0L) header("Range", "bytes=$existing-")
        }.build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: IOException) {
            throw ProviderException(ProviderErrorCode.NETWORK, "Download request failed")
        }
        response.use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                throw ProviderException(ProviderErrorCode.NETWORK, "Download request failed")
            }
            val total = totalBytes(resp.header("Content-Length"), existing, expectedBytes, resp.code)
            val body = resp.body ?: throw ProviderException(ProviderErrorCode.NETWORK, "Download body is empty")
            RandomAccessFile(partialFile, "rw").use { raf ->
                raf.seek(existing)
                val buffer = ByteArray(bufferSize)
                var downloaded = existing
                body.byteStream().use { input ->
                    while (true) {
                        if (isCancelled()) {
                            throw ProviderException(ProviderErrorCode.CANCELLED, "Download was cancelled")
                        }
                        val read = input.read(buffer)
                        if (read <= 0) break
                        raf.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
        }
        return partialFile
    }

    private fun totalBytes(contentLength: String?, existing: Long, expected: Long, code: Int): Long {
        val length = contentLength?.toLongOrNull() ?: 0L
        if (code == 206 && length > 0L) return existing + length
        if (length > 0L) return length
        return expected
    }

    fun promote(partial: File): File {
        val finalFile = File(partial.parentFile, partial.name.removeSuffix(".partial"))
        if (finalFile.exists()) finalFile.delete()
        if (!partial.renameTo(finalFile)) {
            partial.copyTo(finalFile, overwrite = true)
            partial.delete()
        }
        return finalFile
    }
}
