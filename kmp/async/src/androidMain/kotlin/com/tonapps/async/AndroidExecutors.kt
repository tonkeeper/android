package com.tonapps.async

import com.tonapps.log.L
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val uncaughtHandler = Thread.UncaughtExceptionHandler { _, e -> L.e(e) }

internal fun createDefaultPool(name: String): ThreadPoolExecutor {
    return FinalizedThreadPoolExecutor(
        corePoolSize = 0,
        maximumPoolSize = 1,
        keepAliveTime = 10_000L,
        unit = TimeUnit.MILLISECONDS,
        threadFactory = TwThreadFactory(name),
    )
}

internal class FinalizedThreadPoolExecutor(
    corePoolSize: Int,
    maximumPoolSize: Int,
    keepAliveTime: Long,
    unit: TimeUnit,
    threadFactory: ThreadFactory,
    workQueue: BlockingQueue<Runnable> = LinkedBlockingQueue(),
    allowCoreThreadTimeout: Boolean = false,
) : ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory) {

    init {
        if (corePoolSize > 0 && allowCoreThreadTimeout && keepAliveTime > 0L) {
            allowCoreThreadTimeOut(true)
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith("shutdown()"))
    override fun finalize() {
        shutdown()
    }
}

private class TwThreadFactory(
    private val prefix: String,
    private val threadPriority: Int = Thread.MAX_PRIORITY,
) : ThreadFactory {

    private val counter = AtomicInteger()

    override fun newThread(runnable: Runnable): Thread {
        return Thread(runnable, "$prefix-${counter.incrementAndGet()}")
            .apply {
                priority = threadPriority
                uncaughtExceptionHandler = uncaughtHandler
            }
    }
}
