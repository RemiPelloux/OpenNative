package app.gamenative.compat

import com.winlator.container.ContainerData

/** One-shot overlay that does not persist experimental overrides. */
object SafeLaunchPreset {
    private val experimentalKeys = setOf("DXVK_ASYNC", "MESA_VK_WSI_DEBUG", "WINEESYNC")

    fun overlay(data: ContainerData): ContainerData = data.copy(
        lsfgEnabled = false,
        sharpnessEffect = "none",
        sharpnessLevel = 0,
        envVars = stripExperimental(data.envVars),
        rendererPresentMode = "fifo",
    )

    fun changes(original: ContainerData, safe: ContainerData): List<String> = buildList {
        if (original.lsfgEnabled && !safe.lsfgEnabled) add("frame generation")
        if (original.sharpnessEffect != safe.sharpnessEffect) add("sharpening")
        if (original.envVars != safe.envVars) add("experimental environment")
        if (original.rendererPresentMode != safe.rendererPresentMode) add("present mode")
    }

    private fun stripExperimental(value: String): String = value
        .split(Regex("\\s+"))
        .filter { token -> token.substringBefore('=') !in experimentalKeys }
        .joinToString(" ")
        .trim()
}
