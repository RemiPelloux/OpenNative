package app.gamenative.provider

object ProviderCredentials {
    fun hasGlobal(secrets: ProviderSecretStore): Boolean =
        hasGlobal(DebridProvider.ALL_DEBRID, secrets)

    fun hasGlobal(provider: DebridProvider, secrets: ProviderSecretStore): Boolean =
        secrets.read(provider.credentialRef) != null

    fun attachAvailable(tab: ProviderTab, provider: DebridProvider, secrets: ProviderSecretStore): ProviderTab {
        if (hasGlobal(provider, secrets)) return tab.copy(credentialRef = provider.credentialRef)
        if (provider != DebridProvider.ALL_DEBRID) return tab.copy(credentialRef = null)
        return if (secrets.read(tab.credentialRef) != null) tab else tab.copy(credentialRef = null)
    }

    fun attachAvailable(tab: ProviderTab, secrets: ProviderSecretStore): ProviderTab =
        attachAvailable(tab, DebridProvider.ALL_DEBRID, secrets)

    fun requireFor(tab: ProviderTab, secrets: ProviderSecretStore): String =
        requireFor(tab, DebridProvider.ALL_DEBRID, secrets).apiKey

    fun requireFor(tab: ProviderTab, provider: DebridProvider, secrets: ProviderSecretStore): DebridCredential {
        val key = secrets.read(provider.credentialRef)
            ?: if (provider == DebridProvider.ALL_DEBRID) secrets.read(tab.credentialRef) else null
        return DebridCredential(
            provider,
            key ?: throw ProviderException(
                ProviderErrorCode.AUTHENTICATION,
                "Resolver credential is missing",
            ),
        )
    }
}
