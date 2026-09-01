package app.gamenative.container

enum class IsolationTier {
    DEDICATED,
    SHARED_COMPACT,
    NAMED_GROUP,
    LAB,
    ;

    val wireName: String
        get() = name.lowercase()

    companion object {
        fun from(raw: String?, sharedPrefix: Boolean = false): IsolationTier {
            val key = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireName == key || it.name.equals(key, ignoreCase = true) }
                ?: if (sharedPrefix) SHARED_COMPACT else DEDICATED
        }
    }
}

enum class LaunchProfile {
    FAST_BOOT,
    COMPATIBILITY,
    SAFE,
    ;

    val wireName: String
        get() = name.lowercase()

    companion object {
        fun from(raw: String?): LaunchProfile {
            val key = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireName == key || it.name.equals(key, ignoreCase = true) }
                ?: FAST_BOOT
        }
    }
}

enum class ContainerHealth {
    SEALED,
    WARM,
    COLD,
    DIRTY,
    BROKEN,
    LOCKED,
    ;

    companion object {
        fun from(raw: String?): ContainerHealth =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: COLD
    }
}

enum class SessionIoClass {
    PLAY,
    INSTALL,
    MAINTAIN,
}
