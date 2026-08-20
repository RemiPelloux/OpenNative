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
    ): List<ProviderTab> {
        if (PrefManager.providerDefaultsSeeded) return emptyList()
        if (catalog.getTabs().isNotEmpty()) {
            PrefManager.providerDefaultsSeeded = true
            return emptyList()
        }
        val created = ProviderTabCodec.decode(bundleJson).map { catalog.createTab(it) }
        PrefManager.providerDefaultsSeeded = true
        Timber.tag("ProviderTabs").i("Seeded ${created.size} default provider tab(s)")
        return created
    }
}
