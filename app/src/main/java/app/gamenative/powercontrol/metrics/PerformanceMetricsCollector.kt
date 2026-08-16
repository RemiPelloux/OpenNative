package app.gamenative.powercontrol.metrics

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import app.gamenative.performance.adaptive.AdaptivePerformanceObserver
import app.gamenative.performance.adaptive.AdaptivePrediction
import app.gamenative.performance.adaptive.AdaptiveEngineCoordinator
import app.gamenative.powercontrol.PowerBaselineScripts
import app.gamenative.powercontrol.PowerManager
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Headless collector for game performance metrics.
 *
 * Runs for the whole game session independently of the on-screen HUD: frame pacing
 * comes from [FrameTimeRing] (fed by the render/present path), CPU/GPU/thermal
 * readings from [SystemMetricsSources]. Every cycle publishes a [MetricsSnapshot]
 * on [PowerManager] and appends a JSONL record for offline analysis.
 */
object PerformanceMetricsCollector {
    private const val TAG = "PowerMetrics"
    private const val SAMPLE_INTERVAL_MS = 500L
    private const val FRAME_WINDOW_MS = 2_000L
    private const val SLOW_FRAME_FACTOR = 1.5
    private const val LOG_EVERY_N_SAMPLES = 10
    private const val MAX_LOG_BYTES = 20L * 1024L * 1024L
    private const val MAX_SESSION_FILES = 5
    private const val DEFAULT_REFRESH_RATE = 60f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var samplingJob: Job? = null

    private val cpuSampler = CpuUsageSampler()
    private val gpuSampler = GpuUsageSampler()
    private val cadence = MetricsSamplingCadence()

    private val frameScratch = LongArray(FrameTimeRing.capacity())
    private val deltaScratch = LongArray(FrameTimeRing.capacity())

    private val sessionLog = JsonlSessionLog(TAG, "metrics-", MAX_LOG_BYTES, MAX_SESSION_FILES)
    private var sampleCount = 0L
    private var displayRefreshRate = DEFAULT_REFRESH_RATE
    private var cachedCpu: CpuUsageReading? = null
    private var cachedGpu: GpuUsageReading? = null
    private var cachedCpuTempC: Int? = null
    private var cachedGpuTempC: Int? = null
    private var activityManager: ActivityManager? = null
    private val memoryInfo = ActivityManager.MemoryInfo()
    private var cachedAvailableMemoryBytes: Long? = null
    private var cachedTotalMemoryBytes: Long? = null
    private var cachedLowMemory = false
    private var cachedMemoryPressure = LinuxMemoryPressureReading(null, null, null, null)

    @Volatile
    private var paused = false

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(context: Context, sessionStartMillis: Long = System.currentTimeMillis()) {
        if (isRunning) return

        val appContext = context.applicationContext
        displayRefreshRate = readDisplayRefreshRate(appContext)
        cpuSampler.reset()
        gpuSampler.reset()
        cadence.reset()
        cachedCpu = null
        cachedGpu = null
        cachedCpuTempC = null
        cachedGpuTempC = null
        activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        cachedAvailableMemoryBytes = null
        cachedTotalMemoryBytes = null
        cachedLowMemory = false
        cachedMemoryPressure = LinuxMemoryPressureReading(null, null, null, null)
        sampleCount = 0L
        paused = false
        FrameTimeRing.start()
        AdaptivePerformanceObserver.start()
        openLog(appContext, sessionStartMillis)

        val gpuPaths = SystemMetricsSources.gpuUsagePaths()
        Timber.tag(TAG).i(
            "Collector started: frameSource=frame-hook interval=%dms window=%dms refresh=%.1fHz gpu=[%s] cpuTemp=%s gpuTemp=%s log=%s",
            SAMPLE_INTERVAL_MS,
            FRAME_WINDOW_MS,
            displayRefreshRate,
            gpuPaths.joinToString(),
            SystemMetricsSources.cpuTempPaths().firstOrNull() ?: "none",
            SystemMetricsSources.gpuTempPaths().firstOrNull() ?: "none",
            sessionLog.path ?: "none",
        )

        isRunning = true
        samplingJob = scope.launch {
            while (isActive) {
                if (!paused) {
                    runCatching { sampleOnce() }
                        .onFailure { Timber.tag(TAG).e(it, "Sampling cycle failed") }
                }
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        if (!isRunning) return

        isRunning = false
        paused = false
        samplingJob?.cancel()
        samplingJob = null
        FrameTimeRing.stop()
        AdaptivePerformanceObserver.stop()
        closeLog()
        PowerManager.latestMetrics = null
        Timber.tag(TAG).i("Collector stopped after %d samples", sampleCount)
    }

    fun pause() {
        if (!isRunning || paused) return
        paused = true
        sessionLog.flush()
        Timber.tag(TAG).i("Collector paused")
    }

    fun resume() {
        if (!isRunning || !paused) return
        cpuSampler.reset()
        gpuSampler.reset()
        cadence.reset()
        AdaptivePerformanceObserver.start()
        paused = false
        Timber.tag(TAG).i("Collector resumed")
    }

    private fun sampleOnce() {
        val decision = cadence.decide(SystemClock.elapsedRealtime())
        val now = System.nanoTime()
        val frameCount = FrameTimeRing.copySince(now - FRAME_WINDOW_MS * 1_000_000L, frameScratch)
        val frameStats = computeFrameWindowStats(
            frameScratch,
            frameCount,
            slowFrameThresholdNs(),
            deltaScratch,
            PowerManager.frameSampleStride,
        )

        if (decision.sampleResources) {
            cachedCpu = cpuSampler.sample()
            cachedGpu = gpuSampler.sample()
            sampleMemory()
        }
        if (decision.sampleThermals) {
            cachedCpuTempC = SystemMetricsSources.readTemperatureC(SystemMetricsSources.cpuTempPaths())
            cachedGpuTempC = SystemMetricsSources.readTemperatureC(SystemMetricsSources.gpuTempPaths())
        }

        val snapshot = MetricsSnapshot(
            timestampMs = System.currentTimeMillis(),
            fps = frameStats.fps,
            frameTimeP50Ms = frameStats.p50Ms,
            frameTimeP95Ms = frameStats.p95Ms,
            frameTimeMaxMs = frameStats.maxMs,
            slowFrameCount = frameStats.slowFrameCount,
            totalFrameCount = frameStats.totalFrameCount,
            cpuUsagePercent = cachedCpu?.percent?.toFloat(),
            cpuUsageSource = cachedCpu?.source ?: CpuUsageSource.UNAVAILABLE,
            gpuUsagePercent = cachedGpu?.percent?.toFloat(),
            cpuTempC = cachedCpuTempC,
            gpuTempC = cachedGpuTempC,
            availableMemoryBytes = cachedAvailableMemoryBytes,
            totalMemoryBytes = cachedTotalMemoryBytes,
            lowMemory = cachedLowMemory,
            swapUsedBytes = cachedMemoryPressure.swapUsedBytes,
            swapTotalBytes = cachedMemoryPressure.swapTotalBytes,
            memoryPsiSomeAvg10 = cachedMemoryPressure.psiSomeAvg10,
            memoryPsiFullAvg10 = cachedMemoryPressure.psiFullAvg10,
        )

        val prediction = AdaptivePerformanceObserver.observe(snapshot, PowerManager.targetFps)
        AdaptiveEngineCoordinator.observe(snapshot, prediction)
        publish(snapshot)
        if (decision.writeLog) appendLog(snapshot, prediction)

        sampleCount++
        if (sampleCount % LOG_EVERY_N_SAMPLES == 0L) {
            Timber.tag(TAG).i(
                "fps=%.1f p95=%.1fms slow=%d/%d cpu=%s%%(%s) gpu=%s%% cpuTemp=%s gpuTemp=%s adaptive=%s confidence=%.2f",
                snapshot.fps,
                snapshot.frameTimeP95Ms,
                snapshot.slowFrameCount,
                snapshot.totalFrameCount,
                snapshot.cpuUsagePercent?.toInt()?.toString() ?: "-",
                snapshot.cpuUsageSource.name,
                snapshot.gpuUsagePercent?.toInt()?.toString() ?: "-",
                snapshot.cpuTempC?.toString() ?: "-",
                snapshot.gpuTempC?.toString() ?: "-",
                prediction.reason,
                prediction.confidence,
            )
        }
    }

    private fun sampleMemory() {
        try {
            val manager = activityManager ?: return
            manager.getMemoryInfo(memoryInfo)
            cachedAvailableMemoryBytes = memoryInfo.availMem
            cachedTotalMemoryBytes = memoryInfo.totalMem
            cachedLowMemory = memoryInfo.lowMemory
            cachedMemoryPressure = MemoryPressureReader.read()
        } catch (_: Exception) {
            cachedAvailableMemoryBytes = null
            cachedTotalMemoryBytes = null
            cachedLowMemory = false
            cachedMemoryPressure = LinuxMemoryPressureReading(null, null, null, null)
        }
    }

    private fun publish(snapshot: MetricsSnapshot) {
        PowerManager.latestMetrics = snapshot
        PowerManager.currentFps = snapshot.fps
        PowerManager.currentCpuUsage = snapshot.cpuUsagePercent ?: 0f
        PowerManager.currentGpuUsage = snapshot.gpuUsagePercent ?: 0f
    }

    private fun slowFrameThresholdNs(): Long {
        val targetFps = PowerManager.targetFps
        val referenceFps = if (targetFps > 0) targetFps.toFloat() else displayRefreshRate
        if (referenceFps <= 0f) return 0L
        return (SLOW_FRAME_FACTOR * 1_000_000_000.0 / referenceFps).toLong()
    }

    private fun readDisplayRefreshRate(context: Context): Float {
        return try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val rate = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate
                ?: DEFAULT_REFRESH_RATE
            if (rate.isFinite() && rate > 1f) rate else DEFAULT_REFRESH_RATE
        } catch (_: Exception) {
            DEFAULT_REFRESH_RATE
        }
    }

    private fun metricsDirectory(context: Context): File {
        val external = context.getExternalFilesDir(null)
        val parent = external ?: context.filesDir
        return File(parent, PowerBaselineScripts.DIRECTORY_NAME)
    }

    private fun openLog(context: Context, sessionStartMillis: Long) {
        sessionLog.open(metricsDirectory(context), sessionStartMillis)
    }

    private fun appendLog(snapshot: MetricsSnapshot, prediction: AdaptivePrediction) {
        sessionLog.append(toJsonLine(snapshot, prediction))
    }

    private fun toJsonLine(snapshot: MetricsSnapshot, prediction: AdaptivePrediction): String {
        return String.format(
            Locale.US,
            "{\"timestampMs\":%d,\"fps\":%.2f,\"frameTimeP50Ms\":%.2f,\"frameTimeP95Ms\":%.2f," +
                "\"frameTimeMaxMs\":%.2f,\"slowFrameCount\":%d,\"totalFrameCount\":%d," +
                "\"cpuUsagePercent\":%s,\"cpuUsageSource\":\"%s\",\"gpuUsagePercent\":%s," +
                "\"cpuTempC\":%s,\"gpuTempC\":%s,\"availableMemoryBytes\":%s,\"totalMemoryBytes\":%s,\"lowMemory\":%s," +
                "\"swapUsedBytes\":%s,\"swapTotalBytes\":%s,\"memoryPsiSomeAvg10\":%s,\"memoryPsiFullAvg10\":%s," +
                "\"adaptiveBottleneck\":\"%s\",\"adaptiveAdvice\":\"%s\"," +
                "\"predictedP95Ms\":%.2f,\"predictedTemperatureC\":%s,\"adaptiveConfidence\":%.3f}",
            snapshot.timestampMs,
            snapshot.fps,
            snapshot.frameTimeP50Ms,
            snapshot.frameTimeP95Ms,
            snapshot.frameTimeMaxMs,
            snapshot.slowFrameCount,
            snapshot.totalFrameCount,
            snapshot.cpuUsagePercent?.let { String.format(Locale.US, "%.1f", it) } ?: "null",
            snapshot.cpuUsageSource.name,
            snapshot.gpuUsagePercent?.let { String.format(Locale.US, "%.1f", it) } ?: "null",
            snapshot.cpuTempC?.toString() ?: "null",
            snapshot.gpuTempC?.toString() ?: "null",
            snapshot.availableMemoryBytes?.toString() ?: "null",
            snapshot.totalMemoryBytes?.toString() ?: "null",
            snapshot.lowMemory,
            snapshot.swapUsedBytes?.toString() ?: "null",
            snapshot.swapTotalBytes?.toString() ?: "null",
            snapshot.memoryPsiSomeAvg10?.let { String.format(Locale.US, "%.2f", it) } ?: "null",
            snapshot.memoryPsiFullAvg10?.let { String.format(Locale.US, "%.2f", it) } ?: "null",
            prediction.bottleneck.name,
            prediction.resolutionAdvice.name,
            prediction.predictedP95Ms,
            prediction.predictedTemperatureC?.let { String.format(Locale.US, "%.2f", it) } ?: "null",
            prediction.confidence,
        )
    }

    private fun closeLog() {
        sessionLog.close()
    }
}
