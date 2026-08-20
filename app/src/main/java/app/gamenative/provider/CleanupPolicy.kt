package app.gamenative.provider

enum class CleanupPolicy {
    KEEP,
    DELETE_AFTER_VERIFIED_INSTALL,
    ASK,
    ;

    companion object {
        fun fromStored(value: String): CleanupPolicy =
            entries.find { it.name == value } ?: KEEP
    }
}
