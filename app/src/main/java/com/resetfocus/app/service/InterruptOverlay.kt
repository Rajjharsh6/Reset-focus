package com.resetfocus.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.CountDownTimer
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.resetfocus.app.data.Content

/** Full-screen interruption drawn by the accessibility service (no extra overlay permission needed). */
class InterruptOverlay(private val svc: AccessibilityService) {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val ctx = ContextThemeWrapper(svc, android.R.style.Theme_DeviceDefault)
    private var root: View? = null
    private var timer: CountDownTimer? = null

    private val white = 0xFFECECF1.toInt()
    private val muted = 0xFFA0A0AE.toInt()
    private val green = 0xFF3DDC97.toInt()

    private fun dp(v: Int) = (v * svc.resources.displayMetrics.density).toInt()

    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        text = t; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(Typeface.DEFAULT_BOLD)
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun button(t: String, primary: Boolean, onClick: () -> Unit) = Button(ctx).apply {
        text = t; isAllCaps = false; textSize = 16f
        setTextColor(if (primary) 0xFF0B0B0F.toInt() else white)
        background = GradientDrawable().apply {
            cornerRadius = dp(28).toFloat()
            setColor(if (primary) 0xFF8B8BFF.toInt() else 0xFF26262F.toInt())
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply { topMargin = dp(10) }
    }

    private fun container() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setBackgroundColor(0xF20B0B0F.toInt()); isClickable = true
        setPadding(dp(32), dp(64), dp(32), dp(64))
    }

    private fun attach(v: View) {
        dismiss()
        val lp = WindowManager.LayoutParams(
            MATCH_PARENT, MATCH_PARENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        wm.addView(v, lp); root = v
    }

    fun dismiss() {
        timer?.cancel(); timer = null
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }

    fun showBlock(label: String, minutesLeft: Int?) {
        val c = container()
        c.addView(tv("Focus mode is on", 26f, white, true))
        val tail = if (minutesLeft != null) "paused for about $minutesLeft more min" else "paused during your scheduled focus time"
        c.addView(tv("$label is $tail.", 16f, muted))
        c.addView(tv(Content.nudges.random(), 15f, green))
        c.addView(button("Back to what matters", true) { dismiss() })
        attach(c)
    }

    fun showUrge(
        label: String, secs: Int, onClose: () -> Unit, onLog: () -> Unit, onContinue: () -> Unit,
    ) {
        val c = container()
        c.addView(tv("Take a breath", 26f, white, true))
        c.addView(tv("You opened $label. Give yourself $secs seconds before deciding.", 16f, muted))
        c.addView(tv(Content.tips.random(), 15f, green))
        c.addView(tv(Content.activities.random().let { "Instead: $it" }, 14f, muted))
        c.addView(button("Close $label", true) { dismiss(); onClose() })
        c.addView(button("Log this urge", false) { onLog() })
        val cont = button("Continue in ${secs}s", false) { dismiss(); onContinue() }
        cont.isEnabled = false; cont.alpha = 0.4f
        c.addView(cont)
        attach(c)
        timer = object : CountDownTimer(secs * 1000L, 1000) {
            override fun onTick(ms: Long) { cont.text = "Continue in ${ms / 1000 + 1}s" }
            override fun onFinish() { cont.isEnabled = true; cont.alpha = 1f; cont.text = "Continue anyway" }
        }.start()
    }
}
