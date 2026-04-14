package com.tonapps.async

import com.tonapps.log.L
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.jetbrains.annotations.TestOnly
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalAtomicApi::class)
object Async {

    private val exceptionHandler = CoroutineExceptionHandler { _, e -> L.e(e) }

    private val globalJobs = SupervisorJob()

    private val commonScope = CoroutineScope(globalJobs + exceptionHandler)

    // MAIN ASYNC
    private val mainDispatcher = Dispatchers.Main
    private val mainScope by lazy { commonScope + mainDispatcher }

    fun onMain(action: suspend () -> Unit): Job {
        return mainScope.launch {
            action()
        }
    }

    fun mainScope(): CoroutineScope {
        return mainScope
    }

    // BG ASYNC
    val Default: CoroutineDispatcher get() {
        return overrideBgDispatchers
            ?: bgDispatcher
    }

    private var overrideBgDispatchers: CoroutineDispatcher? = null

    private val bgDispatcher = Dispatchers.Default
    private val bgContext by lazy { Default + globalJobs + exceptionHandler }
    private val bgScope by lazy { CoroutineScope(bgContext) }

    fun isMainThread(): Boolean = ThreadChecker.isMainThread()
    fun isStateThread(): Boolean = ThreadChecker.isStateThread()
    fun isStateDiffThread(): Boolean = ThreadChecker.isStateDiffThread()

    @TestOnly
    fun setDefaultDispatcher(dispatcher: CoroutineDispatcher?) {
        overrideBgDispatchers = dispatcher
    }

    fun defaultScope(): CoroutineScope {
        return bgScope
    }

    // IO ASYNC
    val Io: CoroutineDispatcher get() {
        return overrideIoDispatchers
            ?: AsyncPlatform.Io
    }

    private var overrideIoDispatchers: CoroutineDispatcher? = null

    private val ioContext by lazy { Io + globalJobs + exceptionHandler }
    private val ioScope by lazy { CoroutineScope(ioContext) }


    @TestOnly
    fun setIoDispatcher(dispatcher: CoroutineDispatcher?) {
        overrideIoDispatchers = dispatcher
    }

    fun ioContext(): CoroutineContext {
        return Io + exceptionHandler
    }

    fun ioScope(): CoroutineScope {
        return ioScope
    }
    
    fun createDispatcher(name: String): CoroutineDispatcher {
        return AsyncPlatform.createDispatcherPool(STATE_THREAD_NAME)
    }

    // STATE ASYNC
    // Using for state -> new state mapping
    private const val STATE_POOL = 3

    internal const val STATE_THREAD_NAME = "tk-state-thread"
    private val currentStateIndex = AtomicInt(0)
    private var overrideStateDispatchers: CoroutineDispatcher? = null

    private val stateDispatcher by lazy {
        List(STATE_POOL) { num ->
            AsyncPlatform.createDispatcherPool("$STATE_THREAD_NAME-$num")
        }
    }

    fun stateDispatcher(): CoroutineDispatcher {
        return overrideStateDispatchers
            ?: stateDispatcher[currentStateIndex.fetchAndAdd(1) % STATE_POOL]
    }

    @TestOnly
    fun setStateDispatcher(dispatcher: CoroutineDispatcher?) {
        overrideStateDispatchers = dispatcher
    }


    // STATE DIFF ASYNC
    // Using for state -> viewState mapping
    internal const val STATE_DIFF_THREAD_NAME = "tk-diff-thread"
    private const val STATE_DIFF_POOL = 2

    private val currentStateDiffIndex = AtomicInt(0)
    private var overrideStateDiffDispatchers: CoroutineDispatcher? = null

    private val stateDiffDispatcher by lazy {
        List(STATE_DIFF_POOL) { num ->
            AsyncPlatform.createDispatcherPool("$STATE_DIFF_THREAD_NAME-$num")
        }
    }

    fun stateDiffDispatchers(): CoroutineDispatcher {
        return overrideStateDiffDispatchers
            ?: stateDiffDispatcher[currentStateDiffIndex.fetchAndAdd(1) % STATE_DIFF_POOL]
    }

    @TestOnly
    fun setStateDiffDispatcher(dispatcher: CoroutineDispatcher?) {
        overrideStateDiffDispatchers = dispatcher
    }

    // GLOBAL ASYNC
    // Use it for task that should work even the screen is destroyed
    fun globalScope(dispatcher: CoroutineDispatcher? = null): CoroutineScope {
        val scope = if (dispatcher == null) {
            bgScope
        } else {
            bgScope.plus(dispatcher)
        }

        return scope
    }

    @Deprecated("Only for legacy", ReplaceWith("globalScope()"))
    @Suppress("EXPERIMENTAL_API_USAGE")
    fun launchGlobal(
        context: CoroutineContext = bgContext,
        action: suspend CoroutineScope.() -> Unit,
    ): Job {
        @Suppress("OPT_IN_USAGE")
        return GlobalScope.launch(
            context,
            CoroutineStart.DEFAULT,
            action,
        )
    }
}
