package com.tonapps.bus.screenrender

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer

interface ScreenRenderScheduler {
    /** Milliseconds on a monotonic clock, comparable with the process start. */
    val now: Long

    fun runOnMain(action: () -> Unit)

    /** Runs on the main thread at the next frame, in the order the actions were queued. */
    fun onNextFrame(action: () -> Unit)

    fun after(delayMs: Long, action: () -> Unit)
}

internal class AndroidScreenRenderScheduler : ScreenRenderScheduler {

    private val handler = Handler(Looper.getMainLooper())
    private val frameActions = ArrayList<() -> Unit>()
    private var isFrameRequested = false

    override val now: Long
        get() = SystemClock.uptimeMillis()

    override fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post(action)
        }
    }

    override fun onNextFrame(action: () -> Unit) {
        runOnMain {
            frameActions.add(action)
            if (!isFrameRequested) {
                isFrameRequested = true
                Choreographer.getInstance().postFrameCallback { drainFrame() }
            }
        }
    }

    override fun after(delayMs: Long, action: () -> Unit) {
        handler.postDelayed(action, delayMs)
    }

    // One callback per frame keeps the order: a cached stage queued before `finish` is timed first.
    private fun drainFrame() {
        isFrameRequested = false
        val actions = frameActions.toList()
        frameActions.clear()
        actions.forEach { it() }
    }
}
