package app.gamenative.ui.screen.library.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.R
import app.gamenative.provider.ProviderFeedItem
import app.gamenative.provider.ProviderGameUi
import app.gamenative.provider.ProviderLocalPayload
import app.gamenative.provider.TransferJob
import app.gamenative.provider.TransferState
import app.gamenative.ui.theme.DarkColors
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
    val title = ProviderGameUi.title(item)
    val description = ProviderGameUi.description(item)
    val sizeLine = ProviderGameUi.sizeLine(item)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                GameHero(
                    item = item,
                    title = title,
                    sizeLine = sizeLine,
                    artworkUrl = item.artworkUrl,
                    job = job,
                    downloadEnabled = downloadEnabled,
                    onDismiss = onDismiss,
                    onDownload = onDownload,
                    onInstall = onInstall,
                )
                val error = ProviderGameUi.visibleError(job, ProviderLocalPayload.hasInstaller(item))
                if (description.isNotBlank() || error.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (description.isNotBlank()) {
                            Text(description, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (error.isNotBlank()) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHero(
    item: ProviderFeedItem,
    title: String,
    sizeLine: String,
    artworkUrl: String?,
    job: TransferJob?,
    downloadEnabled: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
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
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.85f)),
                    ),
                ),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.statusBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 8f),
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (sizeLine.isNotBlank()) {
                Text(sizeLine, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
            GameActionBar(item, job, downloadEnabled, onDownload, onInstall)
        }
    }
}

@Composable
private fun GameActionBar(
    item: ProviderFeedItem,
    job: TransferJob?,
    downloadEnabled: Boolean,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val downloading = job?.state == TransferState.DOWNLOADING
    val hasLocal = ProviderLocalPayload.hasInstaller(item)
    val canInstall = ProviderGameUi.canInstall(job, item)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = if (canInstall) onInstall else onDownload,
                enabled = canInstall ||
                    (downloadEnabled && !ProviderGameUi.isBusy(job) && !ProviderGameUi.isInstalled(job, item)),
                colors = ButtonDefaults.buttonColors(containerColor = actionColor(job, item, canInstall)),
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                Text(
                    text = actionLabel(job, item, downloadEnabled),
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        if (job != null) {
            Text(
                ProviderGameUi.statusLabel(job, hasLocal),
                color = Color.White.copy(alpha = 0.9f),
            )
        }
        if (ProviderGameUi.isBusy(job) && downloading && job.progressPercent in 1..99) {
            LinearProgressIndicator(
                progress = { job.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (ProviderGameUi.isBusy(job)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun actionLabel(job: TransferJob?, item: ProviderFeedItem, downloadEnabled: Boolean): String = when {
    ProviderGameUi.isInstalled(job, item) -> stringResource(R.string.provider_installed)
    ProviderGameUi.canInstall(job, item) -> stringResource(R.string.provider_install)
    !downloadEnabled -> stringResource(R.string.provider_download_blocked)
    else -> stringResource(R.string.provider_download)
}

private fun actionColor(job: TransferJob?, item: ProviderFeedItem, canInstall: Boolean): Color = when {
    canInstall || ProviderGameUi.isInstalled(job, item) -> DarkColors.statusInstalled
    job?.state == TransferState.DOWNLOADING -> DarkColors.statusDownloading
    else -> DarkColors.statusAvailable
}
