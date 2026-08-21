package app.gamenative.ui.screen.library.provider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.provider.ProviderFeedItem
import app.gamenative.provider.ProviderJobLookup
import app.gamenative.provider.ProviderTab
import app.gamenative.provider.ProviderTabPolicy
import app.gamenative.provider.TransferJob
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ProviderCatalogScreen(
    tab: ProviderTab,
    items: List<ProviderFeedItem>,
    jobs: List<TransferJob>,
    downloadEnabled: Boolean,
    searchQuery: String = "",
    canLoadMore: Boolean = false,
    loadingMore: Boolean = false,
    onSearchQuery: (String) -> Unit = {},
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit = {},
    onDownload: (ProviderFeedItem) -> Unit,
    onInstall: (ProviderFeedItem) -> Unit = {},
    onDeleteTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jobsByItem = remember(jobs) { ProviderJobLookup.latestByItem(jobs) }
    var selected by remember { mutableStateOf<ProviderFeedItem?>(null) }
    val gridState = rememberLazyGridState()
    var searchVisible by remember { mutableStateOf(true) }
    var searchPinned by remember { mutableStateOf(false) }
    val keepSearch = searchQuery.isNotBlank() || searchPinned
    val showSearch = searchVisible || keepSearch
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    LaunchedEffect(gridState, keepSearch) {
        if (keepSearch) {
            searchVisible = true
            return@LaunchedEffect
        }
        var previousIndex = gridState.firstVisibleItemIndex
        var previousOffset = gridState.firstVisibleItemScrollOffset
        var visible = searchVisible
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                visible = ProviderCatalogChrome.nextSearchVisible(
                    visible = visible,
                    firstIndex = index,
                    firstOffset = offset,
                    previousIndex = previousIndex,
                    previousOffset = previousOffset,
                    keepVisible = false,
                )
                searchVisible = visible
                if (!visible) focusManager.clearFocus()
                previousIndex = index
                previousOffset = offset
            }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = 72.dp),
    ) {
        ProviderCatalogHeader(
            title = tab.name,
            showSearchAction = !showSearch,
            onSearch = {
                searchPinned = true
                searchVisible = true
            },
            onRefresh = onRefresh,
            onDeleteTab = onDeleteTab,
        )
        if (tab.stale) {
            Text(
                text = stringResource(R.string.provider_stale),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        AnimatedVisibility(
            visible = showSearch,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ProviderCatalogSearchField(
                query = searchQuery,
                onQueryChange = onSearchQuery,
                onClose = {
                    searchPinned = false
                    if (searchQuery.isBlank()) {
                        searchVisible = false
                        focusManager.clearFocus()
                    }
                },
            )
        }
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.provider_empty),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            ProviderCatalogGrid(
                items = items,
                jobsByItem = jobsByItem,
                gridState = gridState,
                canLoadMore = canLoadMore,
                loadingMore = loadingMore,
                onLoadMore = onLoadMore,
                onSelect = { selected = it },
            )
        }
    }
    selected?.let { item ->
        ProviderGameDialog(
            item = item,
            job = jobsByItem[item.itemId],
            downloadEnabled = downloadEnabled,
            extractOnly = ProviderTabPolicy.extractOnly(tab.feedUrl),
            onDismiss = { selected = null },
            onDownload = { onDownload(item) },
            onInstall = {
                selected = null
                onInstall(item)
            },
        )
    }
}
