package app.gamenative.provider

import java.net.URI

object ProviderUrlPolicy {
    const val MAX_RESPONSE_BYTES = 2_000_000
    const val MAX_ARTWORK_BYTES = 1_500_000
    const val MAX_REDIRECTS = 3
    const val PAGE_SIZE = 100
    const val STARTUP_PAGE_LIMIT = 3

    private val blockedHosts = setOf("google.fr", "facebook.fr")

    fun validate(raw: String, allowLoopbackHttp: Boolean = false): Result<URI> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(ProviderException(ProviderErrorCode.UNSAFE_URL, "Feed URL is required"))
        }
        val uri = runCatching { URI(trimmed) }.getOrElse {
            return Result.failure(ProviderException(ProviderErrorCode.UNSAFE_URL, "Feed URL is malformed"))
        }
        return validateUri(uri, allowLoopbackHttp)
    }

    fun validateUri(uri: URI, allowLoopbackHttp: Boolean = false): Result<URI> {
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase().orEmpty()
        val https = scheme == "https"
        val loopbackHttp = allowLoopbackHttp && scheme == "http" && isLoopback(host)
        if (!https && !loopbackHttp) {
            return Result.failure(ProviderException(ProviderErrorCode.UNSAFE_URL, "Feed URL must use HTTPS"))
        }
        if (host.isBlank()) {
            return Result.failure(ProviderException(ProviderErrorCode.UNSAFE_URL, "Feed URL is missing a host"))
        }
        return Result.success(uri)
    }

    fun isLoopback(host: String): Boolean =
        host == "127.0.0.1" || host == "localhost" || host == "[::1]" || host == "::1"

    fun redact(text: String): String {
        var result = text
        result = API_KEY_QUERY.replace(result, "$1=redacted")
        result = BEARER.replace(result, "Bearer [redacted]")
        return result
    }

    private val API_KEY_QUERY = Regex("(apikey|api_key|token|authorization)=([^&\\s]+)", RegexOption.IGNORE_CASE)
    private val BEARER = Regex("Bearer\\s+\\S+", RegexOption.IGNORE_CASE)
}
