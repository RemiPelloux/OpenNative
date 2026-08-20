package app.gamenative.ui.screen.library.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.provider.ProviderFeedItem
import app.gamenative.provider.ProviderJobLookup
import app.gamenative.provider.ProviderTab
import app.gamenative.provider.ProviderTabPolicy
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
    onInstall: (ProviderFeedItem) -> Unit = {},
    onDeleteTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jobsByItem = remember(jobs) { ProviderJobLookup.latestByItem(jobs) }
    var selected by remember { mutableStateOf<ProviderFeedItem?>(null) }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = 72.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = tab.name, color = colors.onBackground, style = MaterialTheme.typography.titleLarge)
            Row {
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.provider_refresh), color = colors.primary)
                }
                TextButton(onClick = onDeleteTab) {
                    Text(stringResource(R.string.provider_delete), color = colors.primary)
                }
            }
        }
        if (tab.stale) {
            Text(
                text = stringResource(R.string.provider_stale),
                color = colors.onSurfaceVariant,
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
            colors = TextFieldDefaults.colors(
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
            ),
        )
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.provider_empty),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(130.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 128.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.itemId }) { item ->
                    ProviderCatalogRow(
                        item = item,
                        job = jobsByItem[item.itemId],
                        onClick = { selected = item },
                    )
                }
                if (canLoadMore(tab)) {
                    item(key = "load-more", span = { GridItemSpan(maxLineSpan) }) {
                        Button(
                            onClick = onLoadMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Text(stringResource(R.string.provider_load_more))
                        }
                    }
                }
            }
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

private fun canLoadMore(tab: ProviderTab): Boolean {
    if (tab.lastFetchedPage <= 0) return false
    if (tab.totalPages > 0) return tab.lastFetchedPage < tab.totalPages
    return true
}
