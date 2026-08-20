package app.gamenative.provider

enum class ProviderErrorCode {
    AUTHENTICATION,
    RATE_LIMIT,
    UNAVAILABLE_LINK,
    UNSUPPORTED_HOST,
    NETWORK,
    TIMEOUT,
    MALFORMED_RESPONSE,
    UNSAFE_URL,
    LOW_SPACE,
    HASH_MISMATCH,
    PATH_ESCAPE,
    CANCELLED,
    UNKNOWN,
}

class ProviderException(
    val code: ProviderErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    init {
        require(!message.contains("apikey", ignoreCase = true))
        require(!message.contains("bearer ", ignoreCase = true))
    }
}
