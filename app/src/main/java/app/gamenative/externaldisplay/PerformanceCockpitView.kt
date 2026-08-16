package app.gamenative.externaldisplay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import app.gamenative.R
import app.gamenative.powercontrol.PowerManager
import app.gamenative.powercontrol.metrics.MetricsSnapshot
import app.gamenative.performance.adaptive.AdaptiveEngineCoordinator
import app.gamenative.performance.adaptive.AdaptivePerformanceObserver
import app.gamenative.performance.shaders.ShaderHealthMonitor
import com.winlator.widget.TouchpadView
import com.winlator.xserver.XServer
import java.util.Locale
import kotlin.math.max

/** Dense second-screen controls that reuse the session's existing metrics snapshot. */
class PerformanceCockpitView(
    context: Context,
    xServer: XServer,
    touchpadViewProvider: () -> TouchpadView?,
    private val onOpenQuickMenu: () -> Unit,
    private val onTogglePerformanceHud: () -> Unit,
) : FrameLayout(context) {
    private companion object {
        const val GRAPHITE = "#111418"
        const val SURFACE = "#1B2026"
        const val SURFACE_ACTIVE = "#25313A"
        const val TEXT = "#F5F7F8"
        const val TEXT_MUTED = "#9BA7B2"
        const val CYAN = "#36C5F0"
        const val GREEN = "#3DDC84"
        const val DIVIDER = "#33404B"
        const val UPDATE_INTERVAL_MS = 500L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val fpsValue = metricValue()
    private val p95Value = metricValue()
    private val usageValue = metricValue()
    private val temperatureValue = metricValue()
    private val adaptiveValue = TextView(context)
    private val keyboardView = ExternalOnScreenKeyboardView(context, xServer)
    private val keyboardPanel = buildKeyboardPanel()

    private val updateMetrics = object : Runnable {
        override fun run() {
            renderMetrics(PowerManager.latestMetrics)
            mainHandler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    init {
        setBackgroundColor(Color.parseColor(GRAPHITE))
        isFocusable = true
        isFocusableInTouchMode = true

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        content.addView(buildHeader())
        content.addView(buildMetricsBand())
        content.addView(buildAdaptiveBand())
        content.addView(buildActionBand())
        content.addView(buildTouchpad(xServer, touchpadViewProvider))
        addView(content)

        keyboardPanel.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        )
        keyboardPanel.visibility = View.GONE
        keyboardPanel.elevation = dp(8).toFloat()
        addView(keyboardPanel)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mainHandler.removeCallbacks(updateMetrics)
        updateMetrics.run()
    }

    override fun onDetachedFromWindow() {
        mainHandler.removeCallbacks(updateMetrics)
        super.onDetachedFromWindow()
    }

    private fun buildHeader(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(24), 0, dp(24), 0)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(72),
        )
        background = solidBackground(SURFACE)

        addView(TextView(context).apply {
            setText(R.string.cockpit_title)
            setTextColor(Color.parseColor(TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        addView(View(context).apply {
            background = solidBackground(GREEN, cornerDp = 5f)
        }, LinearLayout.LayoutParams(dp(10), dp(10)).apply {
            marginEnd = dp(8)
        })

        addView(TextView(context).apply {
            setText(R.string.cockpit_live)
            setTextColor(Color.parseColor(GREEN))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        })
    }

    private fun buildMetricsBand(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(142),
        )

        addMetric(this, R.string.cockpit_fps, fpsValue, CYAN)
        addDivider(this)
        addMetric(this, R.string.cockpit_frame_p95, p95Value, TEXT)
        addDivider(this)
        addMetric(this, R.string.cockpit_cpu_gpu, usageValue, TEXT)
        addDivider(this)
        addMetric(this, R.string.cockpit_temperature, temperatureValue, GREEN)
    }

    private fun buildActionBand(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(6), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(82),
        )

        addAction(
            parent = this,
            labelRes = R.string.cockpit_quick_menu,
            iconRes = R.drawable.icon_popup_menu_settings,
            onClick = onOpenQuickMenu,
        )
        addAction(
            parent = this,
            labelRes = R.string.cockpit_keyboard,
            iconRes = R.drawable.icon_keyboard,
            onClick = ::toggleKeyboard,
        )
        addAction(
            parent = this,
            labelRes = R.string.cockpit_hud,
            iconRes = R.drawable.icon_popup_menu_cpu,
            onClick = onTogglePerformanceHud,
        )
    }

    private fun buildAdaptiveBand(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(22), 0, dp(22), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        background = solidBackground(SURFACE)

        addView(TextView(context).apply {
            setText(R.string.adaptive_engine_title)
            setTextColor(Color.parseColor(CYAN))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        })
        adaptiveValue.apply {
            text = "--"
            setTextColor(Color.parseColor(TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            gravity = Gravity.END
        }
        addView(adaptiveValue, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(16)
        })
    }

    private fun buildTouchpad(
        xServer: XServer,
        touchpadViewProvider: () -> TouchpadView?,
    ): View = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ).apply {
            setMargins(dp(16), 0, dp(16), dp(16))
        }
        background = outlinedBackground(SURFACE, DIVIDER, cornerDp = 6f)

        addView(TouchpadView(context, xServer, false).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.TRANSPARENT)
            touchpadViewProvider()?.let { primary ->
                setSimTouchScreen(primary.isSimTouchScreen)
            }
        })

        addView(TextView(context).apply {
            setText(R.string.cockpit_touchpad)
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = false
            isFocusable = false
        }, LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START,
        ))
    }

    private fun addMetric(
        parent: LinearLayout,
        labelRes: Int,
        value: TextView,
        accent: String,
    ) {
        val group = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(TextView(context).apply {
                setText(labelRes)
                setTextColor(Color.parseColor(TEXT_MUTED))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            value.setTextColor(Color.parseColor(accent))
            addView(value)
        }
        parent.addView(group, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun addDivider(parent: LinearLayout) {
        parent.addView(View(context).apply {
            setBackgroundColor(Color.parseColor(DIVIDER))
        }, LinearLayout.LayoutParams(dp(1), dp(72)))
    }

    private fun addAction(
        parent: LinearLayout,
        labelRes: Int,
        iconRes: Int,
        onClick: () -> Unit,
    ) {
        val icon = context.getDrawable(iconRes)?.mutate()?.apply {
            val iconSize = dp(24)
            setBounds(0, 0, iconSize, iconSize)
            setTint(Color.parseColor(CYAN))
        }
        val action = TextView(context).apply {
            setText(labelRes)
            setTextColor(Color.parseColor(TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setCompoundDrawablesRelative(icon, null, null, null)
            compoundDrawablePadding = dp(9)
            background = actionBackground()
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(labelRes)
            setOnClickListener { onClick() }
        }
        parent.addView(action, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
            marginStart = dp(5)
            marginEnd = dp(5)
        })
    }

    private fun metricValue(): TextView = TextView(context).apply {
        text = "--"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, dp(5), 0, 0)
    }

    private fun renderMetrics(snapshot: MetricsSnapshot?) {
        fpsValue.text = snapshot?.let { String.format(Locale.US, "%.1f", it.fps) } ?: "--"
        p95Value.text = snapshot?.let { String.format(Locale.US, "%.1f ms", it.frameTimeP95Ms) } ?: "--"
        usageValue.text = snapshot?.let {
            val cpu = it.cpuUsagePercent?.let { value -> String.format(Locale.US, "%.0f", value) } ?: "--"
            val gpu = it.gpuUsagePercent?.let { value -> String.format(Locale.US, "%.0f", value) } ?: "--"
            "$cpu / $gpu%"
        } ?: "--"
        temperatureValue.text = snapshot?.let {
            val temperature = max(it.cpuTempC ?: Int.MIN_VALUE, it.gpuTempC ?: Int.MIN_VALUE)
            if (temperature == Int.MIN_VALUE) "--" else "$temperature\u00B0C"
        } ?: "--"
        val prediction = AdaptivePerformanceObserver.latestPrediction
        val engine = AdaptiveEngineCoordinator.state
        val shader = ShaderHealthMonitor.state
        adaptiveValue.text = if (prediction == null || engine == null) {
            "--"
        } else {
            val confidence = (prediction.confidence * 100).toInt()
            val resolution = engine.pendingResolution?.let { "${engine.activeResolution?.key}>${it.key}" }
                ?: engine.activeResolution?.key.orEmpty()
            "${prediction.bottleneck.name.lowercase()} $confidence%  $resolution  shader:${shader.warmth.name.lowercase()}"
        }
    }

    private fun toggleKeyboard() {
        keyboardPanel.visibility = if (keyboardPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun buildKeyboardPanel(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor(GRAPHITE))

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(8), 0)
            background = solidBackground(SURFACE)

            addView(TextView(context).apply {
                setText(R.string.cockpit_keyboard)
                setTextColor(Color.parseColor(TEXT))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                imageTintList = ColorStateList.valueOf(Color.parseColor(CYAN))
                background = actionBackground()
                contentDescription = context.getString(R.string.close)
                isFocusable = true
                setOnClickListener { toggleKeyboard() }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        addView(
            keyboardView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun actionBackground(): StateListDrawable = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            outlinedBackground(SURFACE_ACTIVE, CYAN, cornerDp = 6f),
        )
        addState(
            intArrayOf(android.R.attr.state_focused),
            outlinedBackground(SURFACE_ACTIVE, CYAN, cornerDp = 6f),
        )
        addState(
            intArrayOf(),
            outlinedBackground(SURFACE, DIVIDER, cornerDp = 6f),
        )
    }

    private fun solidBackground(color: String, cornerDp: Float = 0f): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(cornerDp).toFloat()
        }

    private fun outlinedBackground(
        color: String,
        stroke: String,
        cornerDp: Float,
    ): GradientDrawable = solidBackground(color, cornerDp).apply {
        setStroke(dp(1), Color.parseColor(stroke))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()
}
