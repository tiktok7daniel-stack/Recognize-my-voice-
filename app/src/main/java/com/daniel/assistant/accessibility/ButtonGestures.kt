package com.daniel.assistant.accessibility

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Physical-button gestures that can run a command or start listening. */
enum class ButtonGesture(val id: String, val title: String, val keyCode: Int, val presses: Int, val long: Boolean = false) {
    VOL_UP_DOUBLE("vol_up_double", "دوستين على علّي الصوت", KeyEvent.KEYCODE_VOLUME_UP, 2),
    VOL_DOWN_DOUBLE("vol_down_double", "دوستين على وطّي الصوت", KeyEvent.KEYCODE_VOLUME_DOWN, 2),
    VOL_UP_TRIPLE("vol_up_triple", "٣ دوسات على علّي الصوت", KeyEvent.KEYCODE_VOLUME_UP, 3),
    VOL_DOWN_TRIPLE("vol_down_triple", "٣ دوسات على وطّي الصوت", KeyEvent.KEYCODE_VOLUME_DOWN, 3),
    VOL_UP_LONG("vol_up_long", "ضغطة طويلة على علّي الصوت", KeyEvent.KEYCODE_VOLUME_UP, 1, long = true),
    VOL_DOWN_LONG("vol_down_long", "ضغطة طويلة على وطّي الصوت", KeyEvent.KEYCODE_VOLUME_DOWN, 1, long = true);

    companion object {
        fun byId(id: String?): ButtonGesture? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Detects double / triple / long presses on the volume keys from the accessibility key
 * filter. Normal single presses still change the volume; only the extra presses of a
 * recognized gesture are consumed.
 */
object ButtonGestures {
    private const val MULTI_WINDOW_MS = 450L
    private const val LONG_PRESS_MS = 650L

    /** Gestures that currently have something bound (others pass straight through). */
    @Volatile var active: Set<ButtonGesture> = emptySet()

    private val _events = MutableSharedFlow<ButtonGesture>(extraBufferCapacity = 4)
    val events: SharedFlow<ButtonGesture> = _events

    private val handler = Handler(Looper.getMainLooper())
    private val pressTimes = mutableMapOf<Int, MutableList<Long>>()
    private var longFired = false
    private var pendingMulti: Runnable? = null
    private var longRunnable: Runnable? = null
    private val consumedDown = mutableSetOf<Int>()

    /** Returns true when the event is consumed. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val bound = active.filter { it.keyCode == code }
        if (bound.isEmpty()) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return longFired || consumedDown.contains(code)
                longFired = false
                val now = SystemClock.uptimeMillis()
                val times = pressTimes.getOrPut(code) { mutableListOf() }
                times.removeAll { now - it > MULTI_WINDOW_MS * 3 }
                if (times.isNotEmpty() && now - times.last() > MULTI_WINDOW_MS) times.clear()
                times.add(now)

                bound.firstOrNull { it.long }?.let { g ->
                    longRunnable = Runnable { longFired = true; times.clear(); emit(g) }.also {
                        handler.postDelayed(it, LONG_PRESS_MS)
                    }
                }

                val count = times.size
                pendingMulti?.let(handler::removeCallbacks); pendingMulti = null
                val exact = bound.firstOrNull { !it.long && it.presses == count }
                val higher = bound.any { !it.long && it.presses > count }
                if (exact != null) {
                    consumedDown.add(code)
                    if (higher) {
                        // Wait a moment in case a triple press is coming.
                        pendingMulti = Runnable { times.clear(); emit(exact) }.also { handler.postDelayed(it, MULTI_WINDOW_MS) }
                    } else {
                        times.clear(); emit(exact)
                    }
                    return true
                }
                if (count > 1) {
                    consumedDown.add(code)
                    return true // part of a multi-press: don't keep changing the volume
                }
                return false
            }
            KeyEvent.ACTION_UP -> {
                longRunnable?.let(handler::removeCallbacks); longRunnable = null
                val consumed = longFired || consumedDown.remove(code)
                longFired = false
                return consumed
            }
        }
        return false
    }

    private fun emit(g: ButtonGesture) {
        _events.tryEmit(g)
    }
}
