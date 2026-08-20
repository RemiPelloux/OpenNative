package app.gamenative.provider

import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class ProviderRefreshCoordinator @Inject constructor(
    private val catalog: ProviderCatalogRepository,
) {
    private val dayMs = 24L * 60L * 60L * 1000L

    suspend fun refreshOnOpen(nowMs: Long = System.currentTimeMillis()) {
        if (!ProviderSessionGate.allowCatalogWork()) return
        val tabs = runCatching { refreshEnabled(nowMs) }.getOrElse { error ->
            Timber.tag("ProviderRefresh").w(error, "Daily provider refresh failed")
            return
        }
        Timber.tag("ProviderRefresh").d("Refreshed ${tabs.size} provider tabs")
    }

    suspend fun refreshAllManual() {
        val tabs = currentTabs().filter { it.enabled }
        tabs.forEach { tab ->
            runCatching { catalog.refreshTab(tab) }
                .onFailure { Timber.tag("ProviderRefresh").w(it, "Manual refresh failed") }
        }
    }

    private suspend fun refreshEnabled(nowMs: Long): List<ProviderTab> {
        val refreshed = mutableListOf<ProviderTab>()
        currentTabs().filter { it.enabled && shouldRefresh(it, nowMs) }.forEach { tab ->
            refreshed += catalog.refreshTab(tab, ProviderUrlPolicy.STARTUP_PAGE_LIMIT)
        }
        return refreshed
    }

    private suspend fun currentTabs(): List<ProviderTab> = catalog.getTabs()

    private fun shouldRefresh(tab: ProviderTab, nowMs: Long): Boolean {
        if (tab.refreshPolicy == RefreshPolicy.MANUAL) return false
        if (tab.lastRefreshAtEpochMs <= 0L) return true
        return nowMs - tab.lastRefreshAtEpochMs >= dayMs
    }
}
