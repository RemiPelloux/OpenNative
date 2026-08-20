package app.gamenative.provider

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object StreamingHasher {
    const val BUFFER_BYTES = 64 * 1024

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun matches(expected: String?, actual: String): Boolean {
        if (expected.isNullOrBlank()) return true
        return expected.equals(actual, ignoreCase = true)
    }
}
