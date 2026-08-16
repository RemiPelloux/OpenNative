package app.gamenative.ui.util

import com.winlator.container.ContainerData
import org.json.JSONObject

/** Versioned, path-free representation intended for sharing between devices. */
object PortableContainerProfile {
    const val SCHEMA = "gamenative.portable-container"
    const val VERSION = 1

    private val privateEnvKeys = setOf("EVSHIM_BASE_PATH")

    fun export(
        data: ContainerData,
        fpsLimiterEnabled: Boolean,
        fpsLimiterTarget: Int,
    ): JSONObject = JSONObject().apply {
        put("schema", SCHEMA)
        put("schemaVersion", VERSION)
        put("screenSize", data.screenSize)
        put("envVars", sanitizeEnvironment(data.envVars))
        put("graphicsDriver", data.graphicsDriver)
        put("graphicsDriverVersion", data.graphicsDriverVersion)
        put("graphicsDriverConfig", data.graphicsDriverConfig)
        put("rendererPresentMode", data.rendererPresentMode)
        put("displayRenderer", data.displayRenderer)
        put("sfCompatMode", data.sfCompatMode)
        put("dxwrapper", data.dxwrapper)
        put("dxwrapperConfig", data.dxwrapperConfig)
        put("audioDriver", data.audioDriver)
        put("pulseaudioLowLatency", data.pulseaudioLowLatency)
        put("wincomponents", data.wincomponents)
        put("execArgs", data.execArgs)
        put("executablePath", portableRelativePath(data.executablePath))
        put("showFPS", data.showFPS)
        put("cpuList", data.cpuList)
        put("cpuListWoW64", data.cpuListWoW64)
        put("wow64Mode", data.wow64Mode)
        put("startupSelection", data.startupSelection.toInt())
        put("box86Version", data.box86Version)
        put("box64Version", data.box64Version)
        put("box86Preset", data.box86Preset)
        put("box64Preset", data.box64Preset)
        put("containerVariant", data.containerVariant)
        put("wineVersion", data.wineVersion)
        put("emulator", data.emulator)
        put("fexcoreVersion", data.fexcoreVersion)
        put("fexcoreTSOMode", data.fexcoreTSOMode)
        put("fexcoreX87Mode", data.fexcoreX87Mode)
        put("fexcoreMultiBlock", data.fexcoreMultiBlock)
        put("fexcorePreset", data.fexcorePreset)
        put("renderer", data.renderer)
        put("csmt", data.csmt)
        put("videoPciDeviceID", data.videoPciDeviceID)
        put("offScreenRenderingMode", data.offScreenRenderingMode)
        put("strictShaderMath", data.strictShaderMath)
        put("useDRI3", data.useDRI3)
        put("videoMemorySize", data.videoMemorySize)
        put("mouseWarpOverride", data.mouseWarpOverride)
        put("sdlControllerAPI", data.sdlControllerAPI)
        put("useSteamInput", data.useSteamInput)
        put("enableXInput", data.enableXInput)
        put("enableDInput", data.enableDInput)
        put("dinputMapperType", data.dinputMapperType.toInt())
        put("disableMouseInput", data.disableMouseInput)
        put("touchscreenMode", data.touchscreenMode)
        put("shooterMode", data.shooterMode)
        put("externalDisplayMode", data.externalDisplayMode)
        put("externalDisplaySwap", data.externalDisplaySwap)
        put("language", data.language)
        put("suspendPolicy", data.suspendPolicy)
        put("portraitMode", data.portraitMode)
        put("sharpnessEffect", data.sharpnessEffect)
        put("sharpnessLevel", data.sharpnessLevel)
        put("sharpnessDenoise", data.sharpnessDenoise)
        put("lsfgEnabled", data.lsfgEnabled)
        put("fpsLimiterEnabled", fpsLimiterEnabled)
        put("fpsLimiterTarget", fpsLimiterTarget.coerceAtLeast(5))
    }

    fun apply(base: ContainerData, json: JSONObject): ContainerData = base.copy(
        screenSize = json.string("screenSize", base.screenSize),
        envVars = sanitizeEnvironment(json.string("envVars", base.envVars)),
        graphicsDriver = json.string("graphicsDriver", base.graphicsDriver),
        graphicsDriverVersion = json.string("graphicsDriverVersion", base.graphicsDriverVersion),
        graphicsDriverConfig = json.string("graphicsDriverConfig", base.graphicsDriverConfig),
        rendererPresentMode = json.string("rendererPresentMode", base.rendererPresentMode),
        displayRenderer = json.string("displayRenderer", base.displayRenderer),
        sfCompatMode = json.boolean("sfCompatMode", base.sfCompatMode),
        dxwrapper = json.string("dxwrapper", base.dxwrapper),
        dxwrapperConfig = json.string("dxwrapperConfig", base.dxwrapperConfig),
        audioDriver = json.string("audioDriver", base.audioDriver),
        pulseaudioLowLatency = json.boolean("pulseaudioLowLatency", base.pulseaudioLowLatency),
        wincomponents = json.string("wincomponents", base.wincomponents),
        execArgs = json.string("execArgs", base.execArgs),
        executablePath = portableRelativePath(json.string("executablePath", base.executablePath)),
        showFPS = json.boolean("showFPS", base.showFPS),
        cpuList = json.string("cpuList", base.cpuList),
        cpuListWoW64 = json.string("cpuListWoW64", base.cpuListWoW64),
        wow64Mode = json.boolean("wow64Mode", base.wow64Mode),
        startupSelection = json.byte("startupSelection", base.startupSelection),
        box86Version = json.string("box86Version", base.box86Version),
        box64Version = json.string("box64Version", base.box64Version),
        box86Preset = json.string("box86Preset", base.box86Preset),
        box64Preset = json.string("box64Preset", base.box64Preset),
        containerVariant = json.string("containerVariant", base.containerVariant),
        wineVersion = json.string("wineVersion", base.wineVersion),
        emulator = json.string("emulator", base.emulator),
        fexcoreVersion = json.string("fexcoreVersion", base.fexcoreVersion),
        fexcoreTSOMode = json.string("fexcoreTSOMode", base.fexcoreTSOMode),
        fexcoreX87Mode = json.string("fexcoreX87Mode", base.fexcoreX87Mode),
        fexcoreMultiBlock = json.string("fexcoreMultiBlock", base.fexcoreMultiBlock),
        fexcorePreset = json.string("fexcorePreset", base.fexcorePreset),
        renderer = json.string("renderer", base.renderer),
        csmt = json.boolean("csmt", base.csmt),
        videoPciDeviceID = json.int("videoPciDeviceID", base.videoPciDeviceID),
        offScreenRenderingMode = json.string("offScreenRenderingMode", base.offScreenRenderingMode),
        strictShaderMath = json.boolean("strictShaderMath", base.strictShaderMath),
        useDRI3 = json.boolean("useDRI3", base.useDRI3),
        videoMemorySize = json.string("videoMemorySize", base.videoMemorySize),
        mouseWarpOverride = json.string("mouseWarpOverride", base.mouseWarpOverride),
        sdlControllerAPI = json.boolean("sdlControllerAPI", base.sdlControllerAPI),
        useSteamInput = json.boolean("useSteamInput", base.useSteamInput),
        enableXInput = json.boolean("enableXInput", base.enableXInput),
        enableDInput = json.boolean("enableDInput", base.enableDInput),
        dinputMapperType = json.byte("dinputMapperType", base.dinputMapperType),
        disableMouseInput = json.boolean("disableMouseInput", base.disableMouseInput),
        touchscreenMode = json.boolean("touchscreenMode", base.touchscreenMode),
        shooterMode = json.boolean("shooterMode", base.shooterMode),
        externalDisplayMode = json.string("externalDisplayMode", base.externalDisplayMode),
        externalDisplaySwap = json.boolean("externalDisplaySwap", base.externalDisplaySwap),
        language = json.string("language", base.language),
        suspendPolicy = json.string("suspendPolicy", base.suspendPolicy),
        portraitMode = json.boolean("portraitMode", base.portraitMode),
        sharpnessEffect = json.string("sharpnessEffect", base.sharpnessEffect),
        sharpnessLevel = json.int("sharpnessLevel", base.sharpnessLevel).coerceIn(0, 100),
        sharpnessDenoise = json.int("sharpnessDenoise", base.sharpnessDenoise).coerceIn(0, 100),
        lsfgEnabled = json.boolean("lsfgEnabled", base.lsfgEnabled),
    )

    fun isPortable(json: JSONObject): Boolean =
        json.optString("schema") == SCHEMA && json.optInt("schemaVersion", -1) == VERSION

    private fun sanitizeEnvironment(value: String): String = value
        .split(Regex("\\s+"))
        .filter { token -> token.substringBefore('=') !in privateEnvKeys }
        .joinToString(" ")
        .trim()

    private fun portableRelativePath(value: String): String {
        if (value.startsWith("/") || Regex("^[A-Za-z]:[/\\\\]").containsMatchIn(value)) return ""
        return value
    }

    private fun JSONObject.string(key: String, fallback: String): String =
        if (has(key) && !isNull(key)) optString(key, fallback) else fallback

    private fun JSONObject.boolean(key: String, fallback: Boolean): Boolean =
        if (has(key) && !isNull(key)) optBoolean(key, fallback) else fallback

    private fun JSONObject.int(key: String, fallback: Int): Int =
        if (has(key) && !isNull(key)) optInt(key, fallback) else fallback

    private fun JSONObject.byte(key: String, fallback: Byte): Byte =
        int(key, fallback.toInt()).coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toByte()
}
