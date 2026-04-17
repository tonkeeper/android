package com.tonapps.mvi.thread

import androidx.annotation.AnyThread
import com.tonapps.async.Async
import com.tonapps.log.L
import com.tonapps.mvi.Mvi

internal enum class MviThread {

    Main, State, Computation;

    @AnyThread
    fun require() {
        if (!Mvi.config().useThreadCheck) {
            return
        }

        if (!check()) {
            L.e(IllegalStateException("Call method expected on $name"))
            finish()
        }
    }

    @AnyThread
    fun check(): Boolean {
        return when (this) {
            Main -> Async.isMainThread()
            State -> Async.isStateThread()
            Computation -> Async.isStateDiffThread()
        }
        return true
    }

    @AnyThread
    fun finish() {
        // TODO make platform specific
//        handler.postDelayed(
//            { exitProcess(1) },
//            1000L
//        )
    }
}

