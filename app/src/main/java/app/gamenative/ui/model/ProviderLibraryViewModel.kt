package app.gamenative.ui.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.events.AndroidEvent
import app.gamenative.provider.CatalogFilter
import app.gamenative.provider.DebridProvider
import app.gamenative.provider.DebridResolverRegistry
import app.gamenative.provider.ProviderCatalogPaging
import app.gamenative.provider.ProviderCatalogRepository
import app.gamenative.provider.ProviderCredentials
import app.gamenative.provider.ProviderDefaultTabs
import app.gamenative.provider.ProviderDeviceKeyImport
import app.gamenative.provider.ProviderException
import app.gamenative.provider.ProviderFeedItem
import app.gamenative.provider.ProviderGameUi
import app.gamenative.provider.ProviderInstallHandler
import app.gamenative.provider.ProviderJobLookup
import app.gamenative.provider.ProviderLocalPayload
import app.gamenative.provider.ProviderRefreshCoordinator
import app.gamenative.provider.ProviderSecretStore
import app.gamenative.provider.ProviderTab
import app.gamenative.provider.ProviderTabCodec
import app.gamenative.provider.ProviderTransferCoordinator
import app.gamenative.provider.ProviderUrlPolicy
import app.gamenative.provider.ProviderWineSetup
import app.gamenative.provider.TransferJob
import app.gamenative.provider.TransferState
import app.gamenative.service.ProviderTransferService
import app.gamenative.ui.screen.library.provider.ProviderTabDraft
import app.gamenative.ui.screen.library.provider.toTab
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class ProviderLibraryUi(
    val showCreate: Boolean = false,
    val showKeyDialog: Boolean = false,
    val keyBusy: Boolean = false,
    val createError: String? = null,
    val keyError: String? = null,
    val pendingItem: ProviderFeedItem? = null,
    val keyPromptDismissed: Boolean = false,
    val items: List<ProviderFeedItem> = emptyList(),
    val visibleItems: List<ProviderFeedItem> = emptyList(),
    val jobs: List<TransferJob> = emptyList(),
    val searchQuery: String = "",
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val remoteSearchActive: Boolean = false,
    val hasGlobalCredential: Boolean = false,
    val bundleStatus: String? = null,
    val showGlobalKeyDialog: Boolean = false,
    val showDebridProviderDialog: Boolean = false,
    val selectedDebridProvider: DebridProvider = DebridProvider.ALL_DEBRID,
)

@HiltViewModel
class ProviderLibraryViewModel @Inject constructor(
    private val catalog: ProviderCatalogRepository,
    private val transfers: ProviderTransferCoordinator,
    private val secrets: ProviderSecretStore,
    private val resolverRegistry: DebridResolverRegistry,
    private val refreshCoordinator: ProviderRefreshCoordinator,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val tabs: StateFlow<List<ProviderTab>> = catalog.observeTabs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(ProviderLibraryUi())
    val ui: StateFlow<ProviderLibraryUi> = _ui.asStateFlow()

    init {
        _ui.update { it.copy(selectedDebridProvider = resolverRegistry.selectedProvider) }
        syncGlobalFlag()
    }

    private var activeTabId: String? = null
    private var catalogJob: Job? = null
    private var searchJob: Job? = null
    private var searchPage = 0
    private var searchHasMore = false
    private val searchResults = ArrayList<ProviderFeedItem>()

    fun onAppOpen() {
        viewModelScope.launch {
            runCatching {
                ProviderDeviceKeyImport.consume(
                    context,
                    secrets,
                    resolverRegistry.require(DebridProvider.ALL_DEBRID) as app.gamenative.provider.AllDebridResolver,
                )
            }
            runCatching {
                ProviderDefaultTabs.seedIfEmpty(catalog, ProviderDefaultTabs.readAsset(context))
            }.onFailure { error ->
                Timber.tag("ProviderTabs").e(error, "Default provider tab seed failed")
            }
            syncGlobalFlag()
            refreshCoordinator.refreshOnOpen()
        }
    }

    fun openCreate() = _ui.update { it.copy(showCreate = true, createError = null) }
    fun closeCreate() = _ui.update { it.copy(showCreate = false) }
    fun dismissKeyDialog() = _ui.update {
        it.copy(
            showKeyDialog = false,
            showGlobalKeyDialog = false,
            pendingItem = null,
            keyPromptDismissed = true,
        )
    }

    fun openGlobalKey() = _ui.update { it.copy(showGlobalKeyDialog = true, keyError = null) }

    fun openDebridProvider() = _ui.update { it.copy(showDebridProviderDialog = true) }

    fun dismissDebridProvider() = _ui.update { it.copy(showDebridProviderDialog = false) }

    fun selectDebridProvider(provider: DebridProvider) {
        resolverRegistry.select(provider)
        _ui.update {
            it.copy(
                selectedDebridProvider = provider,
                showDebridProviderDialog = false,
                keyError = null,
            )
        }
        syncGlobalFlag()
    }

    fun selectTab(tabId: String?) {
        if (tabId == activeTabId && catalogJob?.isActive == true) return
        activeTabId = tabId
        catalogJob?.cancel()
        if (tabId == null) return
        resetSearch(clearQuery = true)
        catalogJob = viewModelScope.launch {
            combine(
                catalog.observeItems(tabId),
                transfers.observeJobs(tabId),
                catalog.observeTabs(),
            ) { items, jobs, tabs -> Triple(items, jobs, tabs.find { it.id == tabId }) }
                .collect { (items, jobs, tab) ->
                    _ui.update { state ->
                        val searching = state.remoteSearchActive
                        state.copy(
                            items = items,
                            visibleItems = if (searching) {
                                state.visibleItems
                            } else {
                                CatalogFilter.filter(items, state.searchQuery)
                            },
                            jobs = jobs,
                            canLoadMore = if (searching) {
                                searchHasMore
                            } else {
                                tab != null && ProviderCatalogPaging.canLoadMore(tab)
                            },
                        )
                    }
                }
        }
        viewModelScope.launch { repairStuckCatalog(tabId) }
    }

    private suspend fun repairStuckCatalog(tabId: String) {
        val tab = catalog.getTab(tabId) ?: return
        if (!ProviderCatalogPaging.needsCatalogRepair(tab)) return
        runCatching { catalog.refreshTab(tab) }.onFailure { error ->
            Timber.tag("ProviderTabs").e(error, "Provider catalog repair failed")
        }
    }

    fun createTab(draft: ProviderTabDraft) {
        viewModelScope.launch {
            val name = draft.name.trim()
            if (name.isBlank()) {
                _ui.update { it.copy(createError = context.getString(R.string.provider_need_name)) }
                return@launch
            }
            val url = draft.rssUrl.ifBlank { draft.feedUrl }
            if (url.isBlank()) {
                _ui.update { it.copy(createError = context.getString(R.string.provider_need_url)) }
                return@launch
            }
            runCatching {
                ProviderUrlPolicy.validate(url).getOrThrow()
                persistInstallUri(draft.installTreeUri)
                val created = catalog.createTab(draft.toTab())
                val withKey = saveOptionalKey(created, draft.allDebridKey)
                catalog.refreshTab(withKey)
                _ui.update { it.copy(showCreate = false, createError = null) }
            }.onFailure { error ->
                _ui.update { it.copy(createError = error.message ?: error.toString()) }
            }
        }
    }

    fun onSearchQuery(value: String) {
        _ui.update { it.copy(searchQuery = value) }
        searchJob?.cancel()
        val needle = value.trim()
        if (needle.isEmpty()) {
            resetSearch()
            _ui.update { state ->
                state.copy(visibleItems = CatalogFilter.filter(state.items, ""))
            }
            return
        }
        _ui.update { state ->
            state.copy(visibleItems = CatalogFilter.filter(state.items, needle))
        }
        searchJob = viewModelScope.launch {
            delay(450)
            runRemoteSearch(needle)
        }
    }

    fun refreshActive() {
        val id = activeTabId ?: return
        viewModelScope.launch {
            runCatching {
                catalog.getTab(id)?.let { catalog.refreshTab(it) }
            }.onFailure { error ->
                Timber.tag("ProviderTabs").e(error, "Provider refresh failed")
            }
        }
    }

    fun loadMore() {
        val id = activeTabId ?: return
        if (_ui.value.loadingMore || !_ui.value.canLoadMore) return
        viewModelScope.launch {
            val tab = catalog.getTab(id) ?: return@launch
            _ui.update { it.copy(loadingMore = true) }
            runCatching {
                val needle = _ui.value.searchQuery.trim()
                if (needle.isNotEmpty() && _ui.value.remoteSearchActive) {
                    appendSearchPage(tab, needle)
                } else {
                    catalog.loadMore(tab)
                }
            }.onFailure { error ->
                Timber.tag("ProviderTabs").w(error, "Load more failed")
            }
            _ui.update { it.copy(loadingMore = false) }
        }
    }

    fun refreshAll() {
        viewModelScope.launch { refreshCoordinator.refreshAllManual() }
    }

    fun deleteActive() {
        val id = activeTabId ?: return
        viewModelScope.launch { catalog.deleteTab(id) }
    }

    fun onDownloadClick(tab: ProviderTab, item: ProviderFeedItem) {
        val resolved = tab.withGlobalCredential()
        if (!resolved.hasCredential()) {
            _ui.update { it.copy(showKeyDialog = true, pendingItem = item, keyError = null) }
            return
        }
        val job = ProviderJobLookup.latestByItem(_ui.value.jobs)[item.itemId]
        if (ProviderGameUi.isInstalled(job, item)) return
        if (ProviderGameUi.isBusy(job) && !ProviderGameUi.canRestart(job)) return
        if (ProviderGameUi.canInstall(job, item)) {
            onInstallClick(resolved, item)
            return
        }
        ProviderTransferService.start(context, resolved.id, item.itemId)
    }

    fun onInstallClick(tab: ProviderTab, item: ProviderFeedItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val latest = ProviderJobLookup.latestByItem(_ui.value.jobs)[item.itemId]
            val job = latest ?: if (ProviderLocalPayload.hasInstaller(item)) {
                transfers.attachExisting(tab, item, ProviderLocalPayload.folder(item))
            } else {
                return@launch
            }
            runCatching {
                val ready = latest
                    ?.takeIf { it.state == TransferState.READY && File(it.destinationPath).isDirectory }
                    ?: ProviderInstallHandler.install(transfers, job, item, tab.withGlobalCredential())
                val dest = File(ready.destinationPath.ifBlank { ProviderLocalPayload.folder(item).absolutePath })
                if (!ProviderInstallHandler.shouldLaunchSetup(tab, dest)) return@launch
                val launch = ProviderWineSetup.start(context, dest, tab.cleanupPolicy)
                if (launch != null && ProviderLocalPayload.findInstaller(launch.pack) != null) {
                    PluviaApp.events.emit(AndroidEvent.ExternalGameLaunch(launch.appId))
                }
            }.onFailure { error ->
                transfers.markFailed(job, error, cleanupStaging = true)
            }
        }
    }

    fun saveKey(rawKey: String) = persistGlobalKey(rawKey, fromDownloadPrompt = true)

    fun saveGlobalKey(rawKey: String) = persistGlobalKey(rawKey, fromDownloadPrompt = false)

    fun exportTabs(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val body = ProviderTabCodec.encode(catalog.getTabs())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
                        ?: error("Could not write tab bundle")
                }
                _ui.update { it.copy(bundleStatus = context.getString(R.string.provider_bundle_exported)) }
            }.onFailure { error ->
                _ui.update { it.copy(bundleStatus = error.message ?: error.toString()) }
            }
        }
    }

    fun importTabs(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }
                        ?: error("Could not read tab bundle")
                }
                val incoming = ProviderTabCodec.decode(raw)
                val existing = catalog.getTabs().map { it.feedUrl }.toSet()
                incoming.filter { it.feedUrl !in existing }.forEach { catalog.createTab(it) }
                _ui.update {
                    it.copy(bundleStatus = context.getString(R.string.provider_bundle_imported, incoming.size))
                }
            }.onFailure { error ->
                _ui.update { it.copy(bundleStatus = error.message ?: error.toString()) }
            }
        }
    }

    fun completeInstall(job: TransferJob, exe: File, tab: ProviderTab) {
        viewModelScope.launch {
            val confirmed = tab.cleanupPolicy != app.gamenative.provider.CleanupPolicy.ASK
            runCatching { transfers.completePortableInstall(job, exe, tab.cleanupPolicy, confirmed) }
        }
    }

    fun confirmCleanup(job: TransferJob, exe: File, tab: ProviderTab) {
        viewModelScope.launch {
            runCatching { transfers.completePortableInstall(job, exe, tab.cleanupPolicy, confirmed = true) }
        }
    }

    fun cleanOrphanStaging() {
        viewModelScope.launch {
            runCatching { transfers.cleanOrphanStaging() }
                .onSuccess { count ->
                    _ui.update {
                        it.copy(bundleStatus = context.getString(R.string.provider_settings_orphans_cleaned, count))
                    }
                }
                .onFailure { error ->
                    _ui.update { it.copy(bundleStatus = error.message ?: error.toString()) }
                }
        }
    }

    private fun startDownload(tab: ProviderTab, item: ProviderFeedItem) {
        ProviderTransferService.start(context, tab.id, item.itemId)
    }

    private fun persistInstallUri(uriString: String) {
        if (uriString.isBlank()) return
        val uri = Uri.parse(uriString)
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    private suspend fun saveOptionalKey(tab: ProviderTab, rawKey: String): ProviderTab {
        if (rawKey.isBlank()) return tab.withGlobalCredential()
        persistGlobalKeyValue(rawKey)
        return tab.withGlobalCredential()
    }

    private fun persistGlobalKey(rawKey: String, fromDownloadPrompt: Boolean) {
        viewModelScope.launch {
            _ui.update { it.copy(keyBusy = true, keyError = null) }
            runCatching {
                persistGlobalKeyValue(rawKey)
                val pending = _ui.value.pendingItem
                val tab = activeTabId?.let { catalog.getTab(it) }?.withGlobalCredential()
                _ui.update {
                    it.copy(
                        showKeyDialog = false,
                        showGlobalKeyDialog = false,
                        keyBusy = false,
                        pendingItem = null,
                        hasGlobalCredential = true,
                    )
                }
                if (fromDownloadPrompt && pending != null && tab != null) startDownload(tab, pending)
            }.onFailure { error ->
                val message = if (error is ProviderException) error.message else error.message
                _ui.update { it.copy(keyBusy = false, keyError = message) }
            }
        }
    }

    private suspend fun persistGlobalKeyValue(rawKey: String) {
        withContext(Dispatchers.IO) {
            val provider = resolverRegistry.selectedProvider
            resolverRegistry.require(provider).validateCredential(rawKey)
            val ref = secrets.saveNamed(provider.credentialRef, rawKey)
            PrefManager.providerGlobalCredentialRef = ref
        }
    }

    private fun syncGlobalFlag() {
        _ui.update {
            it.copy(hasGlobalCredential = ProviderCredentials.hasGlobal(resolverRegistry.selectedProvider, secrets))
        }
    }

    private fun ProviderTab.withGlobalCredential(): ProviderTab =
        ProviderCredentials.attachAvailable(this, resolverRegistry.selectedProvider, secrets)

    private fun resetSearch(clearQuery: Boolean = false) {
        searchJob?.cancel()
        searchPage = 0
        searchHasMore = false
        searchResults.clear()
        _ui.update {
            it.copy(
                searchQuery = if (clearQuery) "" else it.searchQuery,
                remoteSearchActive = false,
            )
        }
    }

    private suspend fun runRemoteSearch(needle: String) {
        val tab = activeTabId?.let { catalog.getTab(it) } ?: return
        runCatching {
            val page = catalog.searchPage(tab, needle, page = 1)
            searchResults.clear()
            searchResults += page.items
            searchPage = 1
            searchHasMore = page.hasMore
            _ui.update {
                it.copy(
                    visibleItems = page.items,
                    remoteSearchActive = true,
                    canLoadMore = page.hasMore,
                )
            }
        }.onFailure { error ->
            Timber.tag("ProviderTabs").w(error, "Remote catalog search failed")
        }
    }

    private suspend fun appendSearchPage(tab: ProviderTab, needle: String) {
        if (!searchHasMore) return
        val page = catalog.searchPage(tab, needle, page = searchPage + 1)
        searchResults += page.items
        searchPage += 1
        searchHasMore = page.hasMore
        _ui.update {
            it.copy(
                visibleItems = searchResults.toList(),
                canLoadMore = page.hasMore,
            )
        }
    }
}
