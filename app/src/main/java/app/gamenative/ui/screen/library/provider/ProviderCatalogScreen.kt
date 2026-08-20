package app.gamenative.ui.screen.library.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.provider.ProviderFeedItem
import app.gamenative.provider.ProviderTab
import app.gamenative.provider.TransferJob

@Composable
fun ProviderCatalogScreen(
    tab: ProviderTab,
    items: List<ProviderFeedItem>,
    jobs: List<TransferJob>,
    downloadEnabled: Boolean,
    searchQuery: String = "",
    onSearchQuery: (String) -> Unit = {},
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit = {},
    onDownload: (ProviderFeedItem) -> Unit,
    onDeleteTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jobsByItem = jobs.associateBy { it.itemId }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 72.dp),
    ) {
        Text(
            text = tab.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (tab.stale) {
            Text(
                text = stringResource(R.string.provider_stale),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            label = { Text(stringResource(R.string.provider_search)) },
        )
        TextButton(onClick = onRefresh, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.provider_refresh))
        }
        TextButton(onClick = onDeleteTab, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.provider_delete))
        }
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.provider_empty),
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items, key = { it.itemId }) { item ->
                    ProviderCatalogRow(
                        item = item,
                        job = jobsByItem[item.itemId],
                        downloadEnabled = downloadEnabled,
                        onDownload = { onDownload(item) },
                    )
                }
                if (canLoadMore(tab)) {
                    item(key = "load-more") {
                        TextButton(onClick = onLoadMore, modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.provider_load_more))
                        }
                    }
                }
            }
        }
    }
}

private fun canLoadMore(tab: ProviderTab): Boolean {
    if (tab.lastFetchedPage <= 0) return false
    if (tab.totalPages > 0) return tab.lastFetchedPage < tab.totalPages
    return true
}
