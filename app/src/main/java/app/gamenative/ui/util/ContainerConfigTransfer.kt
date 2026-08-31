package app.gamenative.ui.util

import android.content.Context
import android.net.Uri
import app.gamenative.R
import app.gamenative.ui.screen.library.appscreen.BaseAppScreen
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.BestConfigService
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.ManifestInstaller
import java.io.File
import java.io.IOException
import kotlin.text.Charsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject

object ContainerConfigTransfer {
    suspend fun exportConfig(
        context: Context,
        appId: String,
        uri: Uri,
    ): Boolean {
        return try {
            val jsonText =
                withContext(Dispatchers.IO) {
                    val container = ContainerUtils.getOrCreateContainer(context, appId)
                    PortableContainerProfile.export(
                        data = ContainerUtils.toContainerData(container),
                        fpsLimiterEnabled = container.getExtra("fpsLimiterEnabled", "true").toBooleanStrictOrNull() ?: true,
                        fpsLimiterTarget = container.getExtra("fpsLimiterTarget", "60").toIntOrNull() ?: 60,
                    ).toString(2)
                }

            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonText.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
            }

            SnackbarManager.show(
                context.getString(R.string.base_app_exported),
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            SnackbarManager.show(
                context.getString(
                    R.string.base_app_export_failed,
                    e.message ?: "IO error",
                ),
            )
            false
        } catch (e: Exception) {
            SnackbarManager.show(
                context.getString(
                    R.string.base_app_export_failed,
                    e.message ?: "Unknown error",
                ),
            )
            false
        }
    }

    suspend fun importConfig(
        context: Context,
        appId: String,
        uri: Uri,
        onInstallStateChange: ((visible: Boolean, progress: Float, label: String) -> Unit)? = null,
    ): Boolean {
        return try {
            val jsonText =
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.orEmpty()

            if (jsonText.isBlank()) {
                SnackbarManager.show(
                    context.getString(R.string.best_config_known_config_invalid),
                )
                return false
            }

            // Parse as BestConfig-style JSON
            val configJson: JsonObject =
                withContext(Dispatchers.Default) {
                    Json.parseToJsonElement(jsonText).jsonObject
                }

            val matchType = "exact_gpu_match"

            // 1) Parse config into a validated map of fields to apply
            val bestConfigMap = BestConfigService.parseConfigToContainerData(
                context = context,
                configJson = configJson,
                matchType = matchType,
                applyKnownConfig = true,
            ) ?: emptyMap()

            val missingComponents = BestConfigService.consumeLastMissingComponents()
            if (bestConfigMap.isEmpty()) {
                if (missingComponents.isNotEmpty()) {
                    BaseAppScreen.showMissingComponentsDialog(appId, missingComponents) {
                        // "apply anyway" — re-parse with defaults, install manifest entries, apply
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val forced = BestConfigService.parseConfigToContainerData(
                                    context, configJson, matchType, true, forceApply = true,
                                ) ?: emptyMap()
                                if (forced.isEmpty()) {
                                    SnackbarManager.show(context.getString(R.string.best_config_known_config_invalid))
                                    return@launch
                                }

                                val requests = BestConfigService.resolveMissingManifestInstallRequests(
                                    context, configJson, matchType,
                                )
                                for (request in requests) {
                                    val result = ManifestInstaller.installManifestEntry(
                                        context = context,
                                        entry = request.entry,
                                        isDriver = request.isDriver,
                                        contentType = request.contentType,
                                        onProgress = { _ -> },
                                    )
                                    if (!result.success) {
                                        SnackbarManager.show(result.message)
                                        return@launch
                                    }
                                }

                                val container = ContainerUtils.getOrCreateContainer(context, appId)
                                val currentData = ContainerUtils.toContainerData(container)
                                val updatedData = ContainerUtils.applyBestConfigMapToContainerData(currentData, forced)
                                ContainerUtils.applyToContainer(context, container, updatedData)
                                SnackbarManager.show(context.getString(R.string.best_config_applied_with_defaults))
                            } catch (e: Exception) {
                                SnackbarManager.show(
                                    context.getString(R.string.best_config_apply_failed, e.message ?: "Unknown error"),
                                )
                            }
                        }
                    }
                } else {
                    SnackbarManager.show(
                        context.getString(R.string.best_config_known_config_invalid),
                    )
                }
                return false
            }

            // 2) Install any missing manifest components (wine/proton, dxvk, drivers, etc.)
            val missingRequests = BestConfigService.resolveMissingManifestInstallRequests(
                context = context,
                configJson = configJson,
                matchType = matchType,
            )
            if (missingRequests.isNotEmpty()) {
                onInstallStateChange?.invoke(
                    true,
                    -1f,
                    missingRequests.first().entry.name,
                )
            }
            for (request in missingRequests) {
                val label = request.entry.id
                onInstallStateChange?.invoke(true, -1f, label)
                val result = ManifestInstaller.installManifestEntry(
                    context = context,
                    entry = request.entry,
                    isDriver = request.isDriver,
                    contentType = request.contentType,
                    onProgress = { progress ->
                        onInstallStateChange?.invoke(
                            true,
                            progress.coerceIn(0f, 1f),
                            label,
                        )
                    },
                )
                SnackbarManager.show(result.message)
                if (!result.success) {
                    onInstallStateChange?.invoke(false, -1f, "")
                    return false
                }
            }
            onInstallStateChange?.invoke(false, -1f, "")

            // 3) Apply to container using the same mapping logic as BestConfig
            withContext(Dispatchers.IO) {
                val container = ContainerUtils.getOrCreateContainer(context, appId)
                val currentData = ContainerUtils.toContainerData(container)
                val mappedData = ContainerUtils.applyBestConfigMapToContainerData(currentData, bestConfigMap)
                val originalJson = JSONObject(jsonText)
                val backupDir = File(context.filesDir, "profile-backups")
                val currentJson = PortableContainerProfile.export(
                    currentData,
                    container.getExtra("fpsLimiterEnabled", "true").toBooleanStrictOrNull() ?: true,
                    container.getExtra("fpsLimiterTarget", "60").toIntOrNull() ?: 60,
                )
                ProfileImport.writeBackup(ProfileImport.backupFile(backupDir, appId), currentJson)
                val updatedData = if (PortableContainerProfile.isPortable(originalJson)) {
                    val selected = ProfileCategory.entries.toSet() - ProfileCategory.OTHER
                    val merged = ProfileImport.merge(currentJson, originalJson, selected, replace = true)
                    PortableContainerProfile.apply(mappedData, merged)
                } else {
                    mappedData
                }
                ContainerUtils.applyToContainer(context, container, updatedData)
                if (PortableContainerProfile.isPortable(originalJson)) {
                    container.putExtra(
                        "fpsLimiterEnabled",
                        originalJson.optBoolean("fpsLimiterEnabled", true),
                    )
                    container.putExtra(
                        "fpsLimiterTarget",
                        originalJson.optInt("fpsLimiterTarget", 60).coerceAtLeast(5),
                    )
                    container.saveData()
                }
            }

            SnackbarManager.show(
                context.getString(R.string.best_config_applied_successfully),
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            onInstallStateChange?.invoke(false, -1f, "")
            SnackbarManager.show(
                context.getString(
                    R.string.best_config_apply_failed,
                    e.message ?: "IO error",
                ),
            )
            false
        } catch (e: Exception) {
            onInstallStateChange?.invoke(false, -1f, "")
            SnackbarManager.show(
                context.getString(
                    R.string.best_config_apply_failed,
                    e.message ?: "Unknown error",
                ),
            )
            false
        }
    }
}
