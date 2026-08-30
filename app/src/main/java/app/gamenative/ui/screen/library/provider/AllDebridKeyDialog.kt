package app.gamenative.ui.screen.library.provider

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.gamenative.R
import app.gamenative.provider.DebridProvider

@Composable
fun DebridKeyDialog(
    visible: Boolean,
    provider: DebridProvider,
    busy: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    if (!visible) return
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.provider_key_title, provider.displayName)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.provider_key_body, provider.displayName))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.provider_debrid_key_label, provider.displayName)) },
                )
                if (!errorText.isNullOrBlank()) {
                    Text(errorText)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = key.isNotBlank() && !busy,
                onClick = { onSave(key.trim()) },
            ) {
                Text(stringResource(R.string.provider_key_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.provider_key_dismiss))
            }
        },
    )
}
