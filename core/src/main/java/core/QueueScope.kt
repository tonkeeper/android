package core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Deprecated("Use kotlinx.coroutines.channels.Channel instead")
class QueueScope(context: CoroutineContext) {

    private val scope = CoroutineScope(context)
    private val jobs = mutableListOf<Job>()
    private val queue = Channel<Job>(Channel.UNLIMITED)
    var isCancelled = false

    init {
        scope.launch(Dispatchers.Default) {
            for (job in queue) job.join()
        }
    }

    fun submit(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        val job = scope.launch(context, CoroutineStart.LAZY, block)
        jobs.add(job)
        queue.trySend(job)
        return job
    }

    fun cancel() {
        isCancelled = true
        queue.cancel()
        jobs.forEach { it.cancel() }
        jobs.clear()
        scope.cancel()
    }
}