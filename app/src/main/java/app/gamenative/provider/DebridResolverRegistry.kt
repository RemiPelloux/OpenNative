package app.gamenative.provider

class DebridResolverRegistry(
    resolvers: List<DebridResolver>,
    private val providerStore: DebridProviderStore,
) {
    private val byProvider = resolvers.associateBy(DebridResolver::provider)

    val selectedProvider: DebridProvider
        get() = providerStore.selected

    fun select(provider: DebridProvider) {
        require(provider in byProvider) { "Unsupported debrid provider" }
        providerStore.selected = provider
    }

    fun selected(): DebridResolver = require(selectedProvider)

    fun require(provider: DebridProvider): DebridResolver = byProvider[provider]
        ?: throw ProviderException(ProviderErrorCode.UNKNOWN, "Debrid provider is unavailable")
}
