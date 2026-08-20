package app.gamenative.provider

import android.content.Context
import app.gamenative.PrefManager
import timber.log.Timber

object ProviderDefaultTabs {
    const val ASSET_NAME = "opennative-provider-tabs.json"

    fun readAsset(context: Context): String =
        context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }

    suspend fun seedIfEmpty(
        catalog: ProviderCatalogRepository,
        bundleJson: String,
    ): List<ProviderTab> = ensure(catalog, bundleJson)

    suspend fun ensure(
        catalog: ProviderCatalogRepository,
        bundleJson: String,
    ): List<ProviderTab> {
        val existingUrls = catalog.getTabs().map { it.feedUrl }.toSet()
        val created = ProviderTabCodec.decode(bundleJson)
            .filter { it.feedUrl !in existingUrls }
            .map { catalog.createTab(it) }
        PrefManager.providerDefaultsSeeded = true
        if (created.isNotEmpty()) {
            Timber.tag("ProviderTabs").i("Seeded ${created.size} default provider tab(s)")
        }
        return created
    }
}
