package app.gamenative.ui.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.provider.AllDebridResolver
import app.gamenative.provider.CatalogFilter
import app.gamenative.provider.PayloadClassifier
import app.gamenative.provider.PayloadKind
import app.gamenative.provider.ProviderCatalogRepository
import app.gamenative.provider.ProviderDefaultTabs
import app.gamenative.provider.ProviderDeviceKeyImport
import app.gamenative.provider.ProviderException
import app.gamenative.provider.ProviderFeedItem
import app.gamenative.provider.ProviderInstallHandler
import app.gamenative.provider.ProviderRefreshCoordinator
import app.gamenative.provider.WordpressMetadata
import app.gamenative.provider.ProviderSecretStore
import app.gamenative.provider.ProviderTab
import app.gamenative.provider.ProviderTabBundle
import app.gamenative.provider.ProviderTabCodec
import app.gamenative.provider.ProviderTransferCoordinator
import app.gamenative.provider.ProviderUrlPolicy
import app.gamenative.provider.TransferJob
import app.gamenative.ui.screen.library.provider.ProviderTabDraft
import app.gamenative.ui.screen.library.provider.toTab
import app.gamenative.utils.StorageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val hasGlobalCredential: Boolean = false,
    val bundleStatus: String? = null,
    val showGlobalKeyDialog: Boolean = false,
)

@HiltViewModel
class ProviderLibraryViewModel @Inject constructor(
    private val catalog: ProviderCatalogRepository,
    private val transfers: ProviderTransferCoordinator,
    private val secrets: ProviderSecretStore,
    private val resolver: AllDebridResolver,
    private val refreshCoordinator: ProviderRefreshCoordinator,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val tabs: StateFlow<List<ProviderTab>> = catalog.observeTabs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(ProviderLibraryUi())
    val ui: StateFlow<ProviderLibraryUi> = _ui.asStateFlow()

    init {
        syncGlobalFlag()
    }

    private var activeTabId: String? = null
    private var catalogJob: Job? = null

    fun onAppOpen() {
        viewModelScope.launch {
            runCatching { ProviderDeviceKeyImport.consume(context, secrets, resolver) }
            runCatching {
                ProviderDefaultTabs.seedIfEmpty(catalog, ProviderDefaultTabs.readAsset(context))
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

    fun selectTab(tabId: String?) {
        if (tabId == activeTabId && catalogJob?.isActive == true) return
        activeTabId = tabId
        catalogJob?.cancel()
        if (tabId == null) return
        _ui.update { it.copy(searchQuery = "") }
        catalogJob = viewModelScope.launch {
            combine(
                catalog.observeItems(tabId),
                transfers.observeJobs(tabId),
            ) { items, jobs -> items to jobs }.collect { (items, jobs) ->
                _ui.update { state ->
                    state.copy(
                        items = items,
                        visibleItems = CatalogFilter.filter(items, state.searchQuery),
                        jobs = jobs,
                    )
                }
            }
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
        _ui.update { it.copy(searchQuery = value, visibleItems = CatalogFilter.filter(it.items, value)) }
    }

    fun refreshActive() {
        val id = activeTabId ?: return
        viewModelScope.launch {
            catalog.getTab(id)?.let { catalog.refreshTab(it, search = _ui.value.searchQuery.trim()) }
        }
    }

    fun loadMore() {
        val id = activeTabId ?: return
        viewModelScope.launch {
            catalog.getTab(id)?.let { catalog.loadMore(it, search = _ui.value.searchQuery.trim()) }
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
        startDownload(resolved, item)
    }

    fun onInstallClick(tab: ProviderTab, item: ProviderFeedItem) {
        val job = _ui.value.jobs.lastOrNull { it.itemId == item.itemId } ?: return
        viewModelScope.launch {
            runCatching {
                ProviderInstallHandler.install(transfers, job, item, tab.withGlobalCredential())
            }.onFailure { error ->
                transfers.markFailed(job, error.message ?: error.toString())
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
            runCatching { transfers.completePortableInstall(job, exe, tab.cleanupPolicy, confirmed = true) }
        }
    }

    private fun startDownload(tab: ProviderTab, item: ProviderFeedItem) {
        viewModelScope.launch {
            val available = runCatching {
                StorageUtils.getAvailableSpace(context.filesDir.absolutePath)
            }.getOrDefault(0L)
            val links = WordpressMetadata.candidateLinks(item)
            val downloadItem = item.copy(link = links.firstOrNull() ?: item.link)
            var job: TransferJob? = null
            runCatching {
                job = transfers.enqueue(tab, downloadItem, available, includeWineHeadroom = true)
                val downloaded = transfers.resolveAndDownload(tab, job!!, { false }, links, item.link)
                val kind = PayloadClassifier.classify(File(downloaded.finalPath))
                if (kind == PayloadKind.PORTABLE_ARCHIVE) {
                    ProviderInstallHandler.install(transfers, downloaded, item, tab)
                }
            }.onFailure { error ->
                job?.let { transfers.markFailed(it, error.message ?: error.toString()) }
            }
        }
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
        resolver.validateCredential(rawKey)
        val ref = secrets.saveNamed(ProviderTabBundle.GLOBAL_CREDENTIAL_REF, rawKey)
        PrefManager.providerGlobalCredentialRef = ref
    }

    private fun syncGlobalFlag() {
        _ui.update { it.copy(hasGlobalCredential = PrefManager.providerGlobalCredentialRef.isNotBlank()) }
    }

    private fun ProviderTab.withGlobalCredential(): ProviderTab {
        if (hasCredential()) return this
        val global = PrefManager.providerGlobalCredentialRef
        return if (global.isBlank()) this else copy(credentialRef = global)
    }
}
