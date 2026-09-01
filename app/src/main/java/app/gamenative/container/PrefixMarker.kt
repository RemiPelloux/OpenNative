package app.gamenative.container

import java.io.File
import org.json.JSONObject

data class PrefixMarker(
    val wineId: String,
    val wincomponents: String,
    val dxWrapper: String,
    val graphicsDriver: String,
    val locale: String,
    val imageFsVersion: String,
    val cleanShutdown: Boolean,
) {
    fun matches(expected: PrefixMarker): Boolean =
        wineId == expected.wineId &&
            wincomponents == expected.wincomponents &&
            dxWrapper == expected.dxWrapper &&
            graphicsDriver == expected.graphicsDriver &&
            locale == expected.locale &&
            imageFsVersion == expected.imageFsVersion

    fun toJson(): JSONObject = JSONObject()
        .put("wineId", wineId)
        .put("wincomponents", wincomponents)
        .put("dxWrapper", dxWrapper)
        .put("graphicsDriver", graphicsDriver)
        .put("locale", locale)
        .put("imageFsVersion", imageFsVersion)
        .put("cleanShutdown", cleanShutdown)

    companion object {
        const val FILE_NAME = ".opennative-prefix-marker.json"

        fun file(containerRoot: File): File = File(containerRoot, FILE_NAME)

        fun expected(
            wineId: String,
            wincomponents: String,
            dxWrapper: String,
            graphicsDriver: String,
            locale: String,
            imageFsVersion: String,
        ): PrefixMarker = PrefixMarker(
            wineId = wineId,
            wincomponents = wincomponents,
            dxWrapper = dxWrapper,
            graphicsDriver = graphicsDriver,
            locale = locale,
            imageFsVersion = imageFsVersion,
            cleanShutdown = true,
        )

        fun read(containerRoot: File): PrefixMarker? {
            val file = file(containerRoot)
            if (!file.isFile) return null
            return runCatching { fromJson(JSONObject(file.readText())) }.getOrNull()
        }

        fun write(containerRoot: File, marker: PrefixMarker) {
            containerRoot.mkdirs()
            val tmp = File(containerRoot, "$FILE_NAME.tmp")
            tmp.writeText(marker.toJson().toString())
            if (!tmp.renameTo(file(containerRoot))) {
                file(containerRoot).writeText(marker.toJson().toString())
                tmp.delete()
            }
        }

        fun fromJson(obj: JSONObject): PrefixMarker = PrefixMarker(
            wineId = obj.optString("wineId"),
            wincomponents = obj.optString("wincomponents"),
            dxWrapper = obj.optString("dxWrapper"),
            graphicsDriver = obj.optString("graphicsDriver"),
            locale = obj.optString("locale"),
            imageFsVersion = obj.optString("imageFsVersion"),
            cleanShutdown = obj.optBoolean("cleanShutdown", false),
        )
    }
}

object WarmStartPolicy {
    fun isWarm(existing: PrefixMarker?, expected: PrefixMarker): Boolean =
        existing != null && existing.cleanShutdown && existing.matches(expected)

    fun shouldWineboot(existing: PrefixMarker?, expected: PrefixMarker): Boolean =
        !isWarm(existing, expected)
}
