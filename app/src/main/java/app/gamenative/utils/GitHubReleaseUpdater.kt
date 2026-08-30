package app.gamenative.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import app.gamenative.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val sha256: String? = null,
)

object GitHubReleaseUpdater {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/RemiPelloux/OpenNative/releases/latest"
    private const val MAX_APK_BYTES = 1_500_000_000L
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1_000L
    private const val PREFS_NAME = "app_updater"
    private const val KEY_LAST_SUCCESSFUL_CHECK = "last_successful_check_ms"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun check(context: Context, nowMs: Long = System.currentTimeMillis()): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!BuildConfig.SELF_UPDATE_ENABLED) return@withContext null
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!shouldCheck(preferences.getLong(KEY_LAST_SUCCESSFUL_CHECK, 0L), nowMs)) {
            return@withContext null
        }
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "OpenNative/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Update check failed (${response.code})")
                val release = parseRelease(response.body?.string().orEmpty())
                check(preferences.edit().putLong(KEY_LAST_SUCCESSFUL_CHECK, nowMs).commit()) {
                    "Could not save update check time"
                }
                release.takeIf { isNewerVersion(it.versionName, BuildConfig.VERSION_NAME) }
            }
        }.onFailure { Timber.tag("AppUpdater").w(it, "GitHub release check failed") }.getOrNull()
    }

    suspend fun downloadVerifyAndInstall(
        context: Context,
        update: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(context.cacheDir, "updates/OpenNative-${update.versionName}.apk")
            target.parentFile?.mkdirs()
            val partial = File(target.parentFile, "${target.name}.partial")
            partial.delete()
            download(update.downloadUrl, partial, onProgress)
            update.sha256?.let { expected ->
                check(sha256(partial).equals(expected, ignoreCase = true)) {
                    "Downloaded update checksum did not match"
                }
            }
            verifyArchive(context, partial)
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Could not finalize downloaded update" }
            withContext(Dispatchers.Main) { launchInstaller(context, target) }
            true
        }.onFailure { error ->
            Timber.tag("AppUpdater").e(error, "Update download or verification failed")
        }.getOrDefault(false)
    }

    internal fun parseRelease(raw: String): UpdateInfo {
        val json = JSONObject(raw)
        check(!json.optBoolean("draft") && !json.optBoolean("prerelease")) {
            "Latest GitHub release is not stable"
        }
        val version = json.optString("tag_name")
            .removePrefix("opennative-v")
            .removePrefix("v")
            .trim()
        check(version.isNotBlank()) { "Release version is missing" }
        val assets = json.optJSONArray("assets") ?: error("Release has no assets")
        var selected: JSONObject? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true) &&
                name.contains("modern", ignoreCase = true) &&
                !name.contains("xr", ignoreCase = true)
            ) {
                selected = asset
                break
            }
        }
        val asset = selected ?: error("Release has no compatible modern APK")
        val url = asset.optString("browser_download_url")
        check(isTrustedDownloadUrl(url)) { "Release download URL is not trusted" }
        val digest = asset.optString("digest")
            .takeIf { it.startsWith("sha256:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
        return UpdateInfo(version, url, json.optString("body"), digest)
    }

    internal fun isNewerVersion(candidate: String, current: String): Boolean {
        val left = candidate.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val right = current.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(left.size, right.size)) {
            val comparison = (left.getOrElse(index) { 0 }).compareTo(right.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }

    internal fun shouldCheck(lastSuccessfulCheckMs: Long, nowMs: Long): Boolean =
        lastSuccessfulCheckMs <= 0L
            || nowMs < lastSuccessfulCheckMs
            || nowMs - lastSuccessfulCheckMs >= CHECK_INTERVAL_MS

    private fun isTrustedDownloadUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme == "https"
            && uri.host.equals("github.com", ignoreCase = true)
            && uri.path.orEmpty().startsWith("/RemiPelloux/OpenNative/releases/download/")
    }

    private fun download(url: String, target: File, onProgress: (Float) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "OpenNative/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Update download failed (${response.code})" }
            val body = response.body ?: error("Update download was empty")
            val total = body.contentLength()
            check(total == -1L || total in 1..MAX_APK_BYTES) { "Update size is invalid" }
            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        copied += read
                        check(copied <= MAX_APK_BYTES) { "Update exceeds the size limit" }
                        output.write(buffer, 0, read)
                        onProgress(if (total > 0L) (copied.toFloat() / total).coerceIn(0f, 1f) else -1f)
                    }
                    output.fd.sync()
                }
            }
            check(target.length() > 0L) { "Update download was empty" }
        }
    }

    private fun verifyArchive(context: Context, apk: File) {
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
            ?: error("Downloaded file is not a valid APK")
        check(archive.packageName == context.packageName) { "Update package name does not match" }
        check(archive.longVersionCode > BuildConfig.VERSION_CODE) { "Update version is not newer" }
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        check(signerDigests(archive) == signerDigests(installed)) { "Update signature does not match" }
    }

    private fun signerDigests(info: PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
        }.toSet().also { check(it.isNotEmpty()) { "APK signer is missing" } }

    private fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
