package app.gamenative.ui.screen.library.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.provider.ProviderFeedItem
import app.gamenative.provider.TransferJob
import app.gamenative.utils.StorageUtils

@Composable
fun ProviderCatalogRow(
    item: ProviderFeedItem,
    job: TransferJob?,
    downloadEnabled: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(sizeLabel(item), style = MaterialTheme.typography.bodySmall)
            if (item.architecture.isNotBlank()) {
                Text(
                    stringResource(R.string.provider_arch, item.architecture),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (job != null && job.progressPercent in 1..99) {
                LinearProgressIndicator(
                    progress = { job.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        TextButton(onClick = onDownload, enabled = downloadEnabled) {
            Text(
                if (downloadEnabled) stringResource(R.string.provider_download)
                else stringResource(R.string.provider_download_blocked),
            )
        }
    }
}

private fun sizeLabel(item: ProviderFeedItem): String {
    val download = StorageUtils.formatBinarySize(item.downloadSizeBytes.coerceAtLeast(0L))
    return if (item.uncompressedSizeBytes > 0L) {
        val full = StorageUtils.formatBinarySize(item.uncompressedSizeBytes)
        "$download · $full"
    } else {
        download
    }
}
