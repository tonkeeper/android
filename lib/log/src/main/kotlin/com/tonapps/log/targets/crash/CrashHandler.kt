package com.tonapps.log.targets.crash

import java.util.concurrent.atomic.AtomicBoolean

internal object CrashHandler {

    private val isInstalled = AtomicBoolean(false)

    @Volatile
    private var writable: CrashFileWritable? = null

    fun install(crashWritable: CrashFileWritable) {
        writable = crashWritable

        if (isInstalled.compareAndSet(false, true)) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                try {
                    writable?.write(thread, error)
                } catch (ignored: Throwable) {}

                previous?.uncaughtException(thread, error)
            }
        }
    }
}
