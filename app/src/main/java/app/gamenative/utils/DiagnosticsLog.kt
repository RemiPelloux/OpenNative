package app.gamenative.utils

import android.content.Context
import com.winlator.xenvironment.ImageFs
import java.io.File
import app.gamenative.powercontrol.PowerManager
import app.gamenative.performance.adaptive.AdaptiveEngineCoordinator
import app.gamenative.performance.adaptive.AdaptivePerformanceObserver
import app.gamenative.performance.shaders.ShaderHealthMonitor
import java.util.Locale

object DiagnosticsLog {
    private const val MAX_SOURCE_CHARS = 2 * 1024 * 1024
    fun file(context: Context, appId: String): File =
        File(ImageFs.find(context).rootDir, "usr/tmp/wrapper_diag_$appId.txt")

    fun exists(context: Context, appId: String): Boolean =
        file(context, appId).let { it.exists() && it.length() > 0 }

    fun createSanitizedShareFile(context: Context, appId: String): File {
        val destination = File(context.cacheDir, "opennative-diagnostics-$appId.txt")
        val capabilities = PowerManager.deviceCapabilities
        val metrics = PowerManager.latestMetrics
        val prediction = AdaptivePerformanceObserver.latestPrediction
        val engine = AdaptiveEngineCoordinator.state
        val shader = ShaderHealthMonitor.state
        destination.bufferedWriter().use { output ->
            output.appendLine("OpenNative diagnostics")
            output.appendLine("device=${sanitize(capabilities?.manufacturer)} ${sanitize(capabilities?.model)}")
            output.appendLine("soc=${sanitize(capabilities?.soc)} gpu=${sanitize(capabilities?.gpu)}")
            output.appendLine("snapdragonAdreno=${capabilities?.isSnapdragonAdreno ?: false} performanceHint=${capabilities?.performanceHintAvailable ?: false}")
            output.appendLine("fps=${metrics?.fps?.format()} p95Ms=${metrics?.frameTimeP95Ms?.format()} memoryAvailable=${metrics?.availableMemoryBytes}")
            output.appendLine("bottleneck=${prediction?.bottleneck} confidence=${prediction?.confidence?.format()} predictedP95Ms=${prediction?.predictedP95Ms?.format()}")
            output.appendLine("resolution=${engine?.activeResolution?.key} pending=${engine?.pendingResolution?.key} mode=${engine?.mode}")
            output.appendLine("shader=${shader.warmth} activeBytes=${shader.activeBytes} activeFiles=${shader.activeFiles} inactiveBytes=${shader.inactiveBytes}")
            output.appendLine()
            output.appendLine("Sanitized wrapper log")
            val source = file(context, appId)
            if (source.isFile) {
                source.bufferedReader().useLines { lines ->
                    var written = 0
                    for (line in lines) {
                        if (written >= MAX_SOURCE_CHARS) break
                        val clean = sanitize(line)
                        output.appendLine(clean)
                        written += clean.length + 1
                    }
                }
            }
        }
        return destination
    }

    internal fun sanitize(value: String?): String {
        if (value.isNullOrBlank()) return "--"
        return value
            .replace(
                Regex("(?i)(token|password|secret|api[_-]?key|authorization)\\s*[=:]\\s*[^\\s,;]+"),
                "${'$'}1=<redacted>",
            )
            .replace(Regex("/storage/(?:emulated/\\d+|[A-Fa-f0-9-]+)/[^\\s\"']+"), "<storage-path>")
            .replace(Regex("/data/(?:data|user/\\d+)/[^\\s\"']+"), "<app-path>")
            .replace(Regex("/Users/[^/\\s]+/[^\\s\"']+"), "<host-path>")
    }

    private fun Float.format(): String = String.format(Locale.US, "%.2f", this)
}
