package app.gamenative.performance.device

import android.content.Context
import android.os.Build
import app.gamenative.utils.HardwareUtils
import com.winlator.core.GPUInformation
import java.io.File

data class CpuClusterCapability(
    val policy: String,
    val cpuIds: List<Int>,
    val maxFrequencyKhz: Long?,
)

data class DeviceCapabilityProfile(
    val manufacturer: String,
    val model: String,
    val soc: String?,
    val gpu: String?,
    val isQualcomm: Boolean,
    val isAdreno: Boolean,
    val performanceHintAvailable: Boolean,
    val clusters: List<CpuClusterCapability>,
) {
    val isSnapdragonAdreno: Boolean get() = isQualcomm && isAdreno
}

object DeviceCapabilityDetector {
    private val QUALCOMM_TOKENS = listOf("qualcomm", "snapdragon", "sm8", "sm7", "kalama", "pineapple")

    @Volatile
    private var cached: DeviceCapabilityProfile? = null

    fun detect(context: Context): DeviceCapabilityProfile = cached ?: synchronized(this) {
        cached ?: detect(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            board = Build.BOARD.orEmpty(),
            soc = HardwareUtils.getSOCName(),
            gpu = runCatching { GPUInformation.getRenderer(context) }.getOrNull(),
            performanceHintAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            clusters = readCpuClusters(),
        ).also { cached = it }
    }

    internal fun detect(
        manufacturer: String,
        model: String,
        hardware: String,
        board: String,
        soc: String?,
        gpu: String?,
        performanceHintAvailable: Boolean,
        clusters: List<CpuClusterCapability>,
    ): DeviceCapabilityProfile {
        val identity = listOf(manufacturer, model, hardware, board, soc.orEmpty()).joinToString(" ").lowercase()
        return DeviceCapabilityProfile(
            manufacturer = manufacturer,
            model = model,
            soc = soc,
            gpu = gpu,
            isQualcomm = QUALCOMM_TOKENS.any(identity::contains),
            isAdreno = gpu?.contains("adreno", ignoreCase = true) == true,
            performanceHintAvailable = performanceHintAvailable,
            clusters = clusters,
        )
    }

    private fun readCpuClusters(): List<CpuClusterCapability> {
        val root = File("/sys/devices/system/cpu/cpufreq")
        return root.listFiles { file -> file.isDirectory && file.name.startsWith("policy") }
            ?.sortedBy { it.name.removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE }
            ?.map { policy ->
                CpuClusterCapability(
                    policy = policy.name,
                    cpuIds = parseCpuList(File(policy, "related_cpus").readTextOrNull()),
                    maxFrequencyKhz = File(policy, "cpuinfo_max_freq").readTextOrNull()?.trim()?.toLongOrNull(),
                )
            }.orEmpty()
    }

    internal fun parseCpuList(value: String?): List<Int> {
        if (value.isNullOrBlank()) return emptyList()
        return value.trim().split(Regex("[ ,]+")).flatMap { token ->
            val range = token.split('-')
            when (range.size) {
                1 -> listOfNotNull(range[0].toIntOrNull())
                2 -> {
                    val start = range[0].toIntOrNull()
                    val end = range[1].toIntOrNull()
                    if (start == null || end == null || start > end || end - start > 128) emptyList()
                    else (start..end).toList()
                }
                else -> emptyList()
            }
        }.distinct().sorted()
    }

    private fun File.readTextOrNull(): String? = runCatching { bufferedReader().use { it.readLine() } }.getOrNull()
}
