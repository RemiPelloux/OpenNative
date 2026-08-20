package app.gamenative.ui.screen.library.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.provider.ProviderFeedItem
import app.gamenative.provider.ProviderGameUi
import app.gamenative.provider.TransferJob
import app.gamenative.provider.TransferState
import app.gamenative.utils.StorageUtils
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage

private val CardShape = RoundedCornerShape(20.dp)
private val CoverShape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)

@Composable
fun ProviderCatalogRow(
    item: ProviderFeedItem,
    job: TransferJob?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = ProviderGameUi.title(item)
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceVariant.copy(alpha = 0.32f),
            contentColor = colors.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatalogCover(title = title, artworkUrl = item.artworkUrl)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = catalogMeta(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CatalogProgress(job)
            }
        }
    }
}

@Composable
private fun CatalogCover(title: String, artworkUrl: String?) {
    CoilImage(
        modifier = Modifier
            .fillMaxHeight()
            .width(176.dp)
            .clip(CoverShape)
            .background(MaterialTheme.colorScheme.surface),
        imageModel = { artworkUrl },
        imageOptions = ImageOptions(
            contentScale = ContentScale.Crop,
            contentDescription = title,
        ),
        previewPlaceholder = painterResource(R.drawable.ic_logo_color),
    )
}

@Composable
private fun CatalogProgress(job: TransferJob?) {
    if (job == null || job.state == TransferState.IDLE || job.progressPercent !in 1..99) return
    LinearProgressIndicator(
        progress = { job.progressPercent / 100f },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun catalogMeta(item: ProviderFeedItem): String {
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
