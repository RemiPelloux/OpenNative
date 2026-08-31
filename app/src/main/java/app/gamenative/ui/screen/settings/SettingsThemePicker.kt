package app.gamenative.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.gamenative.enums.AppTheme
import com.materialkolor.PaletteStyle

internal val appearancePaletteChoices = listOf(
    PaletteStyle.TonalSpot to "Soft",
    PaletteStyle.Vibrant to "Vibrant",
    PaletteStyle.Expressive to "Bold",
    PaletteStyle.Neutral to "Neutral",
    PaletteStyle.Rainbow to "Rainbow",
    PaletteStyle.Monochrome to "Mono",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsThemePicker(
    appTheme: AppTheme,
    paletteStyle: PaletteStyle,
    onAppTheme: (AppTheme) -> Unit,
    onPaletteStyle: (PaletteStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTheme.entries.forEach { theme ->
                ThemeSwatch(
                    theme = theme,
                    selected = theme == appTheme,
                    onClick = { onAppTheme(theme) },
                )
            }
        }
        Text(
            text = "Palette",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            appearancePaletteChoices.forEach { (style, label) ->
                FilterChip(
                    selected = style == paletteStyle,
                    onClick = { onPaletteStyle(style) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun ThemeSwatch(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                contentDescription = theme.text
            }
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(if (selected) 3.dp else 1.dp, borderColor, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(swatchColor(theme)),
        )
        Text(
            text = theme.text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun swatchColor(theme: AppTheme): Color = when (theme) {
    AppTheme.AUTO -> Color(0xFF64748B)
    AppTheme.DAY -> Color(0xFFF8FAFC)
    AppTheme.NIGHT -> Color(0xFF1E1B4B)
    AppTheme.AMOLED -> Color(0xFF000000)
    else -> theme.seedColor
}
