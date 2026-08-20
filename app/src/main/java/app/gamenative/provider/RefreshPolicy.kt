package app.gamenative.provider

enum class RefreshPolicy {
    MANUAL,
    DAILY,
    ;

    companion object {
        fun fromStored(value: String): RefreshPolicy =
            entries.find { it.name == value } ?: DAILY
    }
}
