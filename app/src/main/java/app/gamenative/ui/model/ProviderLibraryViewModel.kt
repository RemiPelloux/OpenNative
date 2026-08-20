package app.gamenative.ui.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.R
import app.gamenative.provider.CatalogFilter
import app.gamenative.provider.AllDebridResolver
import app.gamenative.provider.PayloadClassifier
import app.gamenative.provider.PayloadKind
import app.gamenative.provider.ProviderCatalogRepository
import app.gamenative.provider.ProviderException
import app.gamenative.provider.ProviderFeedItem
import app.gamenative.provider.ProviderRefreshCoordinator
import app.gamenative.provider.ProviderSecretStore
import app.gamenative.provider.ProviderTab
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private var activeTabId: String? = null
    private var searchJob: Job? = null
    private var catalogJob: Job? = null

    fun onAppOpen() {
        viewModelScope.launch { refreshCoordinator.refreshOnOpen() }
    }

    fun openCreate() = _ui.update { it.copy(showCreate = true, createError = null) }
    fun closeCreate() = _ui.update { it.copy(showCreate = false) }
    fun dismissKeyDialog() = _ui.update {
        it.copy(showKeyDialog = false, pendingItem = null, keyPromptDismissed = true)
    }

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
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            val id = activeTabId ?: return@launch
            catalog.getTab(id)?.let { catalog.refreshTab(it, search = value.trim()) }
        }
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
        if (!tab.hasCredential()) {
            _ui.update { it.copy(showKeyDialog = true, pendingItem = item, keyError = null) }
            return
        }
        startDownload(tab, item)
    }

    fun saveKey(rawKey: String) {
        val tabId = activeTabId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(keyBusy = true, keyError = null) }
            runCatching {
                resolver.validateCredential(rawKey)
                val tab = catalog.getTab(tabId) ?: return@runCatching
                val ref = secrets.save(rawKey)
                val updated = tab.copy(credentialRef = ref)
                catalog.updateTab(updated)
                val pending = _ui.value.pendingItem
                _ui.update { it.copy(showKeyDialog = false, keyBusy = false, pendingItem = null) }
                if (pending != null) startDownload(updated, pending)
            }.onFailure { error ->
                val message = if (error is ProviderException) error.message else error.message
                _ui.update { it.copy(keyBusy = false, keyError = message) }
            }
        }
    }

    fun completeInstall(job: TransferJob, exe: File, tab: ProviderTab) {
        viewModelScope.launch {
            runCatching { transfers.completePortableInstall(job, exe, tab.cleanupPolicy) }
        }
    }

    private fun startDownload(tab: ProviderTab, item: ProviderFeedItem) {
        viewModelScope.launch {
            val available = runCatching {
                StorageUtils.getAvailableSpace(context.filesDir.absolutePath)
            }.getOrDefault(0L)
            val job = transfers.enqueue(tab, item, available, includeWineHeadroom = true)
            val downloaded = transfers.resolveAndDownload(tab, job) { false }
            runCatching {
                val kind = PayloadClassifier.classify(File(downloaded.finalPath))
                if (kind == PayloadKind.PORTABLE_ARCHIVE) {
                    transfers.extractPortable(downloaded)
                }
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
        if (rawKey.isBlank()) return tab
        resolver.validateCredential(rawKey)
        val ref = secrets.save(rawKey)
        val updated = tab.copy(credentialRef = ref)
        catalog.updateTab(updated)
        return updated
    }
}
