package app.gamenative.provider

import android.content.Context
import app.gamenative.BuildConfig
import java.io.File
import timber.log.Timber

object ProviderDeviceTabImport {
    private const val FILE_NAME = "import-provider-tabs.json"

    suspend fun consume(context: Context, catalog: ProviderCatalogRepository) {
        if (!BuildConfig.DEBUG) return
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        val raw = runCatching { file.readText() }.getOrDefault("").trim()
        if (raw.isBlank()) {
            runCatching { file.delete() }
            return
        }
        val existing = catalog.getTabs().map { it.feedUrl }.toSet()
        ProviderTabCodec.decode(raw)
            .filter { tab -> tab.feedUrl !in existing }
            .forEach { tab -> catalog.refreshTab(catalog.createTab(tab)) }
        runCatching { file.delete() }
        Timber.tag("ProviderTabs").i("Imported device provider tab bundle")
    }
}
