package app.gamenative.provider

import android.content.Context
import app.gamenative.BuildConfig
import app.gamenative.PrefManager
import java.io.File
import timber.log.Timber

object ProviderDeviceKeyImport {
    private const val FILE_NAME = "import-alldebrid.key"

    suspend fun consume(
        context: Context,
        secrets: ProviderSecretStore,
        resolver: AllDebridResolver,
    ) {
        if (!BuildConfig.DEBUG) return
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        val raw = runCatching { file.readText() }.getOrDefault("").trim()
        runCatching { file.delete() }
        if (raw.isBlank()) return
        resolver.validateCredential(raw)
        val ref = secrets.saveNamed(ProviderTabBundle.GLOBAL_CREDENTIAL_REF, raw)
        PrefManager.providerGlobalCredentialRef = ref
        Timber.tag("ProviderKey").i("Imported device AllDebrid key into Keystore")
    }
}
