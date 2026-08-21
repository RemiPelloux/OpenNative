package app.gamenative.ui.screen.library.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.provider.ProviderFeedItem
import app.gamenative.provider.TransferJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@Composable
internal fun ProviderCatalogHeader(
    title: String,
    showSearchAction: Boolean,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteTab: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = colors.onBackground,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showSearchAction) {
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.provider_search),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.provider_refresh),
                tint = colors.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDeleteTab) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.provider_delete),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ProviderCatalogSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        placeholder = { Text(stringResource(R.string.search)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            IconButton(onClick = {
                if (query.isNotBlank()) onQueryChange("") else onClose()
            }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.library_search_close),
                    tint = colors.onSurfaceVariant,
                )
            }
        },
        colors = TextFieldDefaults.colors(
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
        ),
    )
}

@Composable
internal fun ProviderCatalogGrid(
    items: List<ProviderFeedItem>,
    jobsByItem: Map<String, TransferJob>,
    gridState: LazyGridState,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onSelect: (ProviderFeedItem) -> Unit,
) {
    LaunchedEffect(gridState, items.size, canLoadMore, loadingMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (canLoadMore && !loadingMore && lastVisible >= items.lastIndex - 4) {
                    onLoadMore()
                }
            }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(130.dp),
        state = gridState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 128.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.itemId }) { item ->
            ProviderCatalogRow(
                item = item,
                job = jobsByItem[item.itemId],
                onClick = { onSelect(item) },
            )
        }
        if (loadingMore) {
            item(key = "loading-more", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
