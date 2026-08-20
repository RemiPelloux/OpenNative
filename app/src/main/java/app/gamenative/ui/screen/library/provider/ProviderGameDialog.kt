package app.gamenative.ui.screen.library.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.provider.ProviderFeedItem
import app.gamenative.provider.TransferJob
import app.gamenative.provider.TransferState
import app.gamenative.utils.StorageUtils
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage

@Composable
fun ProviderGameDialog(
    item: ProviderFeedItem,
    job: TransferJob?,
    downloadEnabled: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(item.title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoilImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp)),
                    imageModel = { item.artworkUrl },
                    imageOptions = ImageOptions(
                        contentScale = ContentScale.Crop,
                        contentDescription = item.title,
                    ),
                    previewPlaceholder = painterResource(R.drawable.ic_logo_color),
                )
                Text(sizeLabel(item), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.description.isNotBlank()) {
                    Text(item.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (job != null) {
                    Text(statusLabel(job), color = MaterialTheme.colorScheme.primary)
                    if (job.progressPercent in 1..99) {
                        LinearProgressIndicator(
                            progress = { job.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (job.errorMessage.isNotBlank()) {
                        Text(job.errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (canInstall(job)) {
                TextButton(onClick = onInstall) { Text(stringResource(R.string.provider_install)) }
            } else {
                TextButton(onClick = onDownload, enabled = downloadEnabled && !isBusy(job)) {
                    Text(
                        if (downloadEnabled) stringResource(R.string.provider_download)
                        else stringResource(R.string.provider_download_blocked),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.provider_cancel)) }
        },
    )
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

private fun statusLabel(job: TransferJob): String = when (job.state) {
    TransferState.READY -> "Ready in Custom"
    TransferState.DOWNLOADING -> "Downloading ${job.progressPercent}%"
    TransferState.RESOLVING -> "Resolving link"
    TransferState.INSTALLING, TransferState.VERIFYING_INSTALL -> "Installing"
    TransferState.FAILED -> "Download failed"
    TransferState.NEEDS_REVIEW -> "Needs review"
    else -> job.state.name.lowercase().replace('_', ' ')
}

private fun canInstall(job: TransferJob?): Boolean {
    val path = job?.finalPath.orEmpty()
    return path.isNotBlank() && job?.state != TransferState.READY && job?.state != TransferState.DOWNLOADING
}

private fun isBusy(job: TransferJob?): Boolean =
    job?.state == TransferState.DOWNLOADING || job?.state == TransferState.RESOLVING
