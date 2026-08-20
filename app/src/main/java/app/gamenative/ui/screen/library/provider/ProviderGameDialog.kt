package app.gamenative.ui.screen.library.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.R
import app.gamenative.provider.ProviderFeedItem
import app.gamenative.provider.ProviderGameUi
import app.gamenative.provider.TransferJob
import app.gamenative.utils.StorageUtils
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage

private val SheetShape = RoundedCornerShape(28.dp)

@Composable
fun ProviderGameDialog(
    item: ProviderFeedItem,
    job: TransferJob?,
    downloadEnabled: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val title = ProviderGameUi.title(item)
    val description = ProviderGameUi.description(item)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp)
                    .fillMaxHeight(0.92f),
                shape = SheetShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    GameHero(title = title, artworkUrl = item.artworkUrl, onDismiss = onDismiss)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(sizeText(item), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (description.isNotBlank()) {
                            Text(description, style = MaterialTheme.typography.bodyLarge)
                        }
                        JobStatus(job)
                    }
                    GameActions(
                        job = job,
                        downloadEnabled = downloadEnabled,
                        onDownload = onDownload,
                        onInstall = onInstall,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameHero(title: String, artworkUrl: String?, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 8.5f)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
    ) {
        CoilImage(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            imageModel = { artworkUrl },
            imageOptions = ImageOptions(contentScale = ContentScale.Crop, contentDescription = title),
            previewPlaceholder = painterResource(R.drawable.ic_logo_color),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                    ),
                ),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.provider_close),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun JobStatus(job: TransferJob?) {
    if (job == null) return
    Text(ProviderGameUi.statusLabel(job), color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun GameActions(
    job: TransferJob?,
    downloadEnabled: Boolean,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.provider_close))
        }
        if (ProviderGameUi.canInstall(job)) {
            Button(onClick = onInstall, modifier = Modifier.weight(1.4f)) {
                Text(stringResource(R.string.provider_install))
            }
        } else {
            Button(
                onClick = onDownload,
                enabled = downloadEnabled && !ProviderGameUi.isBusy(job),
                modifier = Modifier.weight(1.4f),
            ) {
                Text(
                    if (downloadEnabled) stringResource(R.string.provider_download)
                    else stringResource(R.string.provider_download_blocked),
                )
            }
        }
    }
}

@Composable
private fun sizeText(item: ProviderFeedItem): String {
    val download = StorageUtils.formatBinarySize(item.downloadSizeBytes.coerceAtLeast(0L))
    return if (item.uncompressedSizeBytes > 0L) {
        stringResource(
            R.string.provider_size_full,
            download,
            StorageUtils.formatBinarySize(item.uncompressedSizeBytes),
        )
    } else {
        stringResource(R.string.provider_size, download)
    }
}
