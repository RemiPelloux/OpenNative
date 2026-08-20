package app.gamenative.ui.screen.library.provider

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.provider.CleanupPolicy
import app.gamenative.provider.FeedKind
import app.gamenative.provider.ProviderTab
import app.gamenative.provider.RefreshPolicy

data class ProviderTabDraft(
    val name: String = "",
    val feedUrl: String = "",
    val rssUrl: String = "",
    val allDebridKey: String = "",
    val installTreeUri: String = "",
    val cleanupPolicy: CleanupPolicy = CleanupPolicy.DELETE_AFTER_VERIFIED_INSTALL,
    val refreshPolicy: RefreshPolicy = RefreshPolicy.DAILY,
    val perPage: String = "100",
    val orderBy: String = "date",
    val order: String = "desc",
)

@Composable
fun AddProviderTabDialog(
    visible: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onSave: (ProviderTabDraft) -> Unit,
) {
    if (!visible) return
    var step by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(ProviderTabDraft()) }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) draft = draft.copy(installTreeUri = uri.toString())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stepTitle(step)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    0 -> IdentityStep(draft) { draft = it }
                    1 -> FeedStep(draft) { draft = it }
                    else -> InstallStep(draft, folderPicker::launch) { draft = it }
                }
                if (!errorText.isNullOrBlank()) {
                    Text(text = errorText)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (step < 2) step += 1 else onSave(draft)
                },
            ) {
                Text(
                    if (step < 2) stringResource(R.string.provider_next)
                    else stringResource(R.string.provider_save_tab),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (step == 0) onDismiss() else step -= 1
                },
            ) {
                Text(
                    if (step == 0) stringResource(R.string.provider_cancel)
                    else stringResource(R.string.provider_back),
                )
            }
        },
    )
}

@Composable
private fun stepTitle(step: Int): String = when (step) {
    0 -> stringResource(R.string.provider_step_identity)
    1 -> stringResource(R.string.provider_step_feed)
    else -> stringResource(R.string.provider_step_install)
}

@Composable
private fun IdentityStep(draft: ProviderTabDraft, onChange: (ProviderTabDraft) -> Unit) {
    OutlinedTextField(
        value = draft.name,
        onValueChange = { onChange(draft.copy(name = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.provider_tab_name)) },
        singleLine = true,
    )
}

@Composable
private fun FeedStep(draft: ProviderTabDraft, onChange: (ProviderTabDraft) -> Unit) {
    OutlinedTextField(
        value = draft.feedUrl,
        onValueChange = { onChange(draft.copy(feedUrl = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.provider_feed_url)) },
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.rssUrl,
        onValueChange = { onChange(draft.copy(rssUrl = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.provider_rss_url)) },
        supportingText = { Text(stringResource(R.string.provider_rss_hint)) },
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.allDebridKey,
        onValueChange = { onChange(draft.copy(allDebridKey = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.provider_alldebrid_optional)) },
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.perPage,
        onValueChange = { onChange(draft.copy(perPage = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.provider_per_page)) },
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.orderBy,
        onValueChange = { onChange(draft.copy(orderBy = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.provider_order_by)) },
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.order,
        onValueChange = { onChange(draft.copy(order = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.provider_order)) },
        singleLine = true,
    )
}

@Composable
private fun InstallStep(
    draft: ProviderTabDraft,
    onPickFolder: (Uri?) -> Unit,
    onChange: (ProviderTabDraft) -> Unit,
) {
    TextButton(onClick = { onPickFolder(null) }) {
        Text(stringResource(R.string.provider_pick_folder))
    }
    if (draft.installTreeUri.isNotBlank()) {
        Text(draft.installTreeUri, maxLines = 2)
    }
    PolicyButtons(draft, onChange)
}

@Composable
private fun PolicyButtons(draft: ProviderTabDraft, onChange: (ProviderTabDraft) -> Unit) {
    TextButton(onClick = { onChange(draft.copy(cleanupPolicy = CleanupPolicy.KEEP)) }) {
        Text(stringResource(R.string.provider_cleanup_keep))
    }
    TextButton(onClick = { onChange(draft.copy(cleanupPolicy = CleanupPolicy.DELETE_AFTER_VERIFIED_INSTALL)) }) {
        Text(stringResource(R.string.provider_cleanup_delete))
    }
    TextButton(onClick = { onChange(draft.copy(refreshPolicy = RefreshPolicy.DAILY)) }) {
        Text(stringResource(R.string.provider_refresh_daily))
    }
    TextButton(onClick = { onChange(draft.copy(refreshPolicy = RefreshPolicy.MANUAL)) }) {
        Text(stringResource(R.string.provider_refresh_manual))
    }
}

fun ProviderTabDraft.toTab(): ProviderTab {
    val rss = rssUrl.trim()
    val json = feedUrl.trim()
    val useRss = rss.isNotBlank()
    return ProviderTab(
        id = "",
        name = name.trim(),
        position = 0,
        feedUrl = if (useRss) rss else json,
        feedKind = if (useRss) FeedKind.RSS else FeedKind.JSON,
        installTreeUri = installTreeUri,
        cleanupPolicy = cleanupPolicy,
        refreshPolicy = refreshPolicy,
        perPage = perPage.toIntOrNull()?.coerceIn(1, 100) ?: 100,
        orderBy = orderBy.ifBlank { "date" },
        order = order.ifBlank { "desc" },
    )
}
