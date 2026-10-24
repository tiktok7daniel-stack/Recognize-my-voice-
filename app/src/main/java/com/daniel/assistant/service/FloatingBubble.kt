package com.daniel.assistant.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import com.daniel.assistant.R
import kotlin.math.abs

/** Draggable floating mic bubble: tap it anywhere to give a command without the wake word. */
class FloatingBubble(private val context: Context, private val onTap: () -> Unit) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: ImageView? = null
    private var params: WindowManager.LayoutParams? = null

    val isShowing: Boolean get() = view != null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null || !Settings.canDrawOverlays(context)) return
        val density = context.resources.displayMetrics.density
        val size = (60 * density).toInt()
        val p = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = context.resources.displayMetrics.widthPixels - size - (12 * density).toInt()
            y = (context.resources.displayMetrics.heightPixels * 0.45f).toInt()
        }
        val v = ImageView(context).apply {
            setImageResource(R.drawable.ic_splash)
            setBackgroundResource(R.drawable.bubble_bg)
            val pad = (4 * density).toInt()
            setPadding(pad, pad, pad, pad)
            elevation = 8 * density
            contentDescription = "Nabra"
        }
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x; startY = p.y; touchX = e.rawX; touchY = e.rawY; moved = false
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX; val dy = e.rawY - touchY
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    p.x = startX + dx.toInt(); p.y = startY + dy.toInt()
                    runCatching { wm.updateViewLayout(v, p) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (!moved) onTap()
                    true
                }
                else -> false
            }
        }
        runCatching { wm.addView(v, p) }.onSuccess {
            view = v; params = p
        }
    }

    fun setActive(active: Boolean) {
        view?.animate()?.scaleX(if (active) 1.18f else 1f)?.scaleY(if (active) 1.18f else 1f)
            ?.alpha(if (active) 1f else 0.92f)?.setDuration(200)?.start()
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        params = null
    }
}
