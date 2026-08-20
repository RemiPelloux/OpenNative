package app.gamenative.ui.screen.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import app.gamenative.ui.model.ProviderLibraryViewModel
import app.gamenative.ui.theme.settingsTileColors
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink

@Composable
fun SettingsGroupProviders(
    viewModel: ProviderLibraryViewModel = hiltViewModel(),
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    SettingsGroup() {
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.provider_settings_refresh_all)) },
            subtitle = {
                Text(stringResource(R.string.provider_settings_refresh_subtitle))
            },
            onClick = viewModel::refreshAll,
        )
        tabs.forEach { tab ->
            SettingsMenuLink(
                colors = settingsTileColors(),
                title = { Text(tab.name) },
                subtitle = { Text(tab.feedUrl) },
                onClick = { viewModel.selectTab(tab.id); viewModel.refreshActive() },
            )
        }
    }
}
