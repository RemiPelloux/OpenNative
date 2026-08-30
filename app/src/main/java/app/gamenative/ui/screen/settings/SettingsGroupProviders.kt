package app.gamenative.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import app.gamenative.ui.model.ProviderLibraryViewModel
import app.gamenative.ui.screen.library.provider.DebridKeyDialog
import app.gamenative.ui.theme.settingsTileColors
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink

@Composable
fun SettingsGroupProviders(
    viewModel: ProviderLibraryViewModel = hiltViewModel(),
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val export = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportTabs) }
    val importer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importTabs) }
    SettingsGroup {
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.provider_settings_service)) },
            subtitle = { Text(ui.selectedDebridProvider.displayName) },
            onClick = viewModel::openDebridProvider,
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.provider_settings_key, ui.selectedDebridProvider.displayName)) },
            subtitle = {
                Text(
                    if (ui.hasGlobalCredential) {
                        stringResource(R.string.provider_settings_key_set)
                    } else {
                        stringResource(R.string.provider_settings_key_subtitle)
                    },
                )
            },
            onClick = viewModel::openGlobalKey,
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.provider_settings_export)) },
            subtitle = { Text(stringResource(R.string.provider_settings_export_subtitle)) },
            onClick = { export.launch("opennative-provider-tabs.json") },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.provider_settings_import)) },
            subtitle = { Text(stringResource(R.string.provider_settings_import_subtitle)) },
            onClick = { importer.launch(arrayOf("application/json", "text/plain")) },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.provider_settings_refresh_all)) },
            subtitle = { Text(stringResource(R.string.provider_settings_refresh_subtitle)) },
            onClick = viewModel::refreshAll,
        )
        if (!ui.bundleStatus.isNullOrBlank()) {
            SettingsMenuLink(
                colors = settingsTileColors(),
                title = { Text(ui.bundleStatus.orEmpty()) },
                onClick = {},
            )
        }
        tabs.forEach { tab ->
            SettingsMenuLink(
                colors = settingsTileColors(),
                title = { Text(tab.name) },
                subtitle = { Text(tab.feedUrl) },
                onClick = {
                    viewModel.selectTab(tab.id)
                    viewModel.refreshActive()
                },
            )
        }
    }
    DebridKeyDialog(
        visible = ui.showGlobalKeyDialog,
        provider = ui.selectedDebridProvider,
        busy = ui.keyBusy,
        errorText = ui.keyError,
        onDismiss = viewModel::dismissKeyDialog,
        onSave = viewModel::saveGlobalKey,
    )
    DebridProviderDialog(
        visible = ui.showDebridProviderDialog,
        selected = ui.selectedDebridProvider,
        onSelect = viewModel::selectDebridProvider,
        onDismiss = viewModel::dismissDebridProvider,
    )
}
