package app.gamenative.provider

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

data class MagnetUpload(
    val id: String,
    val ready: Boolean,
    val name: String = "",
    val sizeBytes: Long = 0L,
)

class AllDebridMagnetApi(
    private val http: AllDebridHttp,
    private val pollMs: Long = 3_000L,
    private val attempts: Int = 200,
) {
    fun upload(apiKey: String, magnet: String): MagnetUpload {
        http.requireKey(apiKey)
        if (!magnet.startsWith("magnet:?", ignoreCase = true)) {
            throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "No magnet link in this post")
        }
        val json = http.post("/v4/magnet/upload", apiKey, listOf("magnets[]" to magnet))
        if (json.optString("status") != "success") {
            throw http.mapError(json, ProviderErrorCode.UNAVAILABLE_LINK)
        }
        return parseUpload(json)
    }

    suspend fun waitReady(apiKey: String, magnetId: String) {
        repeat(attempts) {
            val status = statusCode(apiKey, magnetId)
            if (status == 4) return
            if (status >= 5) {
                throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Magnet processing failed")
            }
            delay(pollMs)
        }
        throw ProviderException(ProviderErrorCode.TIMEOUT, "Magnet processing timed out")
    }

    fun files(apiKey: String, magnetId: String): List<MagnetRemoteFile> {
        val json = http.post("/v4/magnet/files", apiKey, listOf("id[]" to magnetId.toString()))
        if (json.optString("status") != "success") {
            throw http.mapError(json, ProviderErrorCode.UNAVAILABLE_LINK)
        }
        val magnets = json.optJSONObject("data")?.optJSONArray("magnets") ?: JSONArray()
        val first = magnets.optJSONObject(0) ?: JSONObject()
        if (first.has("error")) throw http.mapError(first, ProviderErrorCode.UNAVAILABLE_LINK)
        val nodes = first.optJSONArray("files") ?: JSONArray()
        val files = AllDebridMagnetFiles.flatten(nodes)
        Timber.tag("ProviderTransfer").i("Magnet $magnetId has ${files.size} files")
        return files
    }

    private fun parseUpload(json: JSONObject): MagnetUpload {
        val magnet = json.optJSONObject("data")?.optJSONArray("magnets")?.optJSONObject(0)
            ?: throw ProviderException(ProviderErrorCode.MALFORMED_RESPONSE, "Magnet upload is malformed")
        if (magnet.has("error")) throw http.mapError(magnet, ProviderErrorCode.UNAVAILABLE_LINK)
        val id = magnet.optInt("id")
        if (id <= 0) {
            throw ProviderException(ProviderErrorCode.UNAVAILABLE_LINK, "Magnet upload did not return an id")
        }
        return MagnetUpload(
            id = id.toString(),
            ready = magnet.optBoolean("ready"),
            name = magnet.optString("name"),
            sizeBytes = magnet.optLong("size"),
        )
    }

    private fun statusCode(apiKey: String, magnetId: String): Int {
        val json = http.post("/v4.1/magnet/status", apiKey, listOf("id" to magnetId.toString()))
        if (json.optString("status") != "success") {
            throw http.mapError(json, ProviderErrorCode.UNAVAILABLE_LINK)
        }
        val data = json.optJSONObject("data") ?: return 0
        val magnets = data.opt("magnets")
        val row = when (magnets) {
            is JSONObject -> magnets
            is JSONArray -> magnets.optJSONObject(0)
            else -> null
        } ?: return 0
        return row.optInt("statusCode")
    }
}
