package app.gamenative.provider

enum class DebridProvider(
    val displayName: String,
    val credentialRef: String,
) {
    ALL_DEBRID("AllDebrid", ProviderTabBundle.GLOBAL_CREDENTIAL_REF),
    REAL_DEBRID("Real-Debrid", "global_realdebrid"),
    PREMIUMIZE("Premiumize", "global_premiumize"),
    DEBRID_LINK("Debrid-Link", "global_debridlink"),
    TORBOX("TorBox", "global_torbox"),
    ;

    companion object {
        fun fromStored(value: String?): DebridProvider =
            entries.firstOrNull { it.name == value } ?: ALL_DEBRID
    }
}

data class DebridCredential(
    val provider: DebridProvider,
    val apiKey: String,
)
