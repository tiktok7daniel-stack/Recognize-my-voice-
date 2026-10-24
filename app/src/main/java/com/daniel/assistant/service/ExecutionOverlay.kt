package com.daniel.assistant.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.daniel.assistant.R

/**
 * Small panel at the top of the screen while a command (or the AI) runs:
 * shows the current step and a Stop button. Needs "display over other apps".
 */
class ExecutionOverlay(private val context: Context, private val onStop: () -> Unit) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var title: TextView? = null
    private var subtitle: TextView? = null
    private var progress: ProgressBar? = null

    fun show(titleText: String, step: String, current: Int, total: Int) {
        if (!Settings.canDrawOverlays(context)) return
        if (root == null) create()
        title?.text = titleText
        subtitle?.text = step
        progress?.apply {
            isIndeterminate = total <= 0
            max = total.coerceAtLeast(1)
            this.progress = current
        }
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null; title = null; subtitle = null; progress = null
    }

    private fun create() {
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val font = runCatching { ResourcesCompat.getFont(context, R.font.tajawal_bold) }.getOrNull()
        val font2 = runCatching { ResourcesCompat.getFont(context, R.font.tajawal_regular) }.getOrNull()

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val t = TextView(context).apply {
            setTextColor(0xFFEEF2F7.toInt()); textSize = 14f
            typeface = font ?: Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        val s = TextView(context).apply {
            setTextColor(0xFF4FE3FF.toInt()); textSize = 12f
            typeface = font2 ?: Typeface.DEFAULT
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF4FE3FF.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x33FFFFFF)
        }
        texts.addView(t); texts.addView(s)
        texts.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)).apply { topMargin = dp(4) })

        val stop = TextView(context).apply {
            text = "■ وقف"
            setTextColor(0xFFFFFFFF.toInt()); textSize = 13f
            typeface = font ?: Typeface.DEFAULT_BOLD
            setBackgroundResource(R.drawable.overlay_stop_bg)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { onStop() }
        }
        val r = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.overlay_bg)
            setPadding(dp(14), dp(10), dp(10), dp(10))
            elevation = 10 * d
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(stop, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10) })
        }
        val width = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
        val p = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(36)
        }
        runCatching { wm.addView(r, p) }.onSuccess {
            root = r; title = t; subtitle = s; progress = bar
        }
    }
}
