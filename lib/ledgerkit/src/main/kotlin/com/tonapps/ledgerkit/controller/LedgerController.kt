package com.tonapps.ledgerkit.controller

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.isRetryable
import com.tonapps.ledgerkit.session.LedgerAppInfo
import com.tonapps.ledgerkit.session.LedgerSession
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.LedgerTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

interface LedgerScope {

    val session: LedgerSession

    fun report(progress: LedgerProgress)

    suspend fun ensureApp(app: LedgerAppId): LedgerAppInfo
}

class LedgerController(
    private val transport: LedgerTransport,
    private val scope: CoroutineScope,
    private val idleTimeout: Duration = 30.seconds,
    private val doneHold: Duration = 900.milliseconds,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow<LedgerOperationState>(LedgerOperationState.Idle)
    val state: StateFlow<LedgerOperationState> = _state.asStateFlow()

    private val _link = MutableStateFlow<LedgerOpenLink?>(null)
    val link: StateFlow<LedgerOpenLink?> = _link.asStateFlow()

    private val _results = MutableSharedFlow<LedgerOperationResult>(extraBufferCapacity = RESULTS_BUFFER)
    val results: SharedFlow<LedgerOperationResult> = _results.asSharedFlow()

    private val mutex = Mutex()

    @Volatile
    private var attempt: Deferred<*>? = null

    @Volatile
    private var cancelledByUser = false

    @Volatile
    private var decision: CompletableDeferred<Boolean>? = null

    @Volatile
    private var activeSession: LedgerSession? = null

    @Volatile
    private var activeApp: LedgerAppId? = null

    private val idleLock = Any()
    private var idleSession: LedgerSession? = null
    private var idleCloseJob: Job? = null
    private var doneResetJob: Job? = null

    suspend fun <T> run(request: LedgerRequest, block: suspend LedgerScope.() -> T): T {
        if (!mutex.tryLock()) {
            throw LedgerFailureException(LedgerFailure.Busy)
        }
        val startedAt = clock()
        doneResetJob?.cancel()
        activeApp = null
        val session = acquireSession(request.deviceId)
        activeSession = session
        publishLink()
        var keepSession = false
        var outcome = LedgerOperationOutcome.Cancelled
        var attempts = 0
        var lastFailure: LedgerFailure? = null
        val errors = mutableListOf<LedgerFailureException>()
        var model: LedgerModel? = null
        var linkKind: LedgerLinkKind? = null
        try {
            while (true) {
                attempts++
                cancelledByUser = false
                _state.value = LedgerOperationState.Active(request, LedgerProgress.Connecting)
                val failure = try {
                    val result = attempt(request, session, block)
                    keepSession = true
                    outcome = LedgerOperationOutcome.Success
                    finishWithDone(request)
                    return result
                } catch (e: LedgerFailureException) {
                    if (e.failure != LedgerFailure.Cancelled) {
                        errors += e
                    }
                    e.failure
                } catch (e: CancellationException) {
                    if (!cancelledByUser) {
                        _state.value = LedgerOperationState.Idle
                        throw e
                    }
                    LedgerFailure.Cancelled
                } finally {
                    model = session.model ?: model
                    linkKind = session.currentLinkKind ?: linkKind
                }
                session.close()
                if (failure == LedgerFailure.Cancelled) {
                    _state.value = LedgerOperationState.Idle
                    throw LedgerFailureException(failure, isPresented = true)
                }
                lastFailure = failure
                if (!awaitRetryDecision(request, failure)) {
                    outcome = LedgerOperationOutcome.Failed
                    _state.value = LedgerOperationState.Idle
                    throw LedgerFailureException(failure, isPresented = true)
                }
            }
        } finally {
            attempt = null
            decision = null
            activeSession = null
            if (_state.value !is LedgerOperationState.Done) {
                _state.value = LedgerOperationState.Idle
            }
            if (keepSession) {
                parkSession(session)
            } else {
                session.close()
            }
            publishLink()
            _results.tryEmit(
                LedgerOperationResult(
                    request = request,
                    outcome = outcome,
                    attempts = attempts,
                    durationMs = clock() - startedAt,
                    lastFailure = lastFailure,
                    errors = errors.toList(),
                    app = activeApp,
                    model = model,
                    linkKind = linkKind,
                ),
            )
            mutex.unlock()
        }
    }

    fun cancel() {
        val pendingDecision = decision
        if (pendingDecision != null && !pendingDecision.isCompleted) {
            pendingDecision.complete(false)
            return
        }
        cancelledByUser = true
        attempt?.cancel(CancellationException("Cancelled by user"))
    }

    fun retry() {
        decision?.complete(true)
    }

    fun dismiss() {
        val pendingDecision = decision
        if (pendingDecision != null) {
            pendingDecision.complete(false)
            return
        }
        if (_state.value is LedgerOperationState.Done) {
            _state.value = LedgerOperationState.Idle
        }
    }

    fun closeIdleSession() {
        val (session, watcher) = takeIdleSession()
        watcher?.cancel()
        session?.close()
    }

    private fun publishLink() {
        _link.value = openLink()
    }

    fun openLink(): LedgerOpenLink? {
        val session = activeSession ?: synchronized(idleLock) { idleSession } ?: return null
        if (!session.isOpen) {
            return null
        }
        return LedgerOpenLink(session.deviceId, session.lastKnownApp, session.linkKind)
    }

    private suspend fun <T> attempt(
        request: LedgerRequest,
        session: LedgerSession,
        block: suspend LedgerScope.() -> T,
    ): T = coroutineScope {
        val ledgerScope = object : LedgerScope {
            override val session: LedgerSession = session

            override fun report(progress: LedgerProgress) {
                progress.targetApp?.let { activeApp = it }
                _state.value = LedgerOperationState.Active(request, progress)
            }

            override suspend fun ensureApp(app: LedgerAppId): LedgerAppInfo {
                val info = session.ensureApp(app)
                report(LedgerProgress.Working(app))
                return info
            }
        }
        session.reporter = ledgerScope::report
        val deferred = async { runBlock(ledgerScope, block) }
        attempt = deferred
        try {
            deferred.await()
        } catch (e: CancellationException) {
            if (cancelledByUser) {
                throw LedgerFailureException(LedgerFailure.Cancelled)
            }
            throw e
        } finally {
            session.reporter = {}
        }
    }

    private suspend fun <T> runBlock(ledgerScope: LedgerScope, block: suspend LedgerScope.() -> T): T = try {
        ledgerScope.block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: LedgerFailureException) {
        throw e
    } catch (e: Throwable) {
        throw LedgerFailureException(LedgerFailure.Unexpected(e.message ?: e.javaClass.simpleName), cause = e)
    }

    private suspend fun awaitRetryDecision(request: LedgerRequest, failure: LedgerFailure): Boolean {
        val pending = CompletableDeferred<Boolean>()
        decision = pending
        _state.value = LedgerOperationState.Failed(request, failure, canRetry = failure.isRetryable)
        return try {
            pending.await()
        } finally {
            decision = null
        }
    }

    private fun finishWithDone(request: LedgerRequest) {
        _state.value = LedgerOperationState.Done(request)
        doneResetJob = scope.launch {
            delay(doneHold)
            if (_state.value == LedgerOperationState.Done(request)) {
                _state.value = LedgerOperationState.Idle
            }
        }
    }

    private suspend fun acquireSession(deviceId: String): LedgerSession {
        val (parked, watcher) = takeIdleSession()
        watcher?.let { withContext(NonCancellable) { it.cancelAndJoin() } }
        if (parked != null && parked.deviceId == deviceId) {
            parked.forgetCurrentApp()
            return parked
        }
        parked?.close()
        return LedgerSession(transport, deviceId).also { it.onLinkChanged = ::publishLink }
    }

    private fun takeIdleSession(): Pair<LedgerSession?, Job?> = synchronized(idleLock) {
        val watcher = idleCloseJob
        idleCloseJob = null
        (idleSession to watcher).also { idleSession = null }
    }

    private fun parkSession(session: LedgerSession) {
        if (!session.isOpen) {
            session.close()
            return
        }
        synchronized(idleLock) {
            idleSession = session
            idleCloseJob = scope.launch {
                launch {
                    session.awaitDisconnected()
                    if (!session.isOpen) {
                        expireIdleSession(session)
                    }
                }
                delay(idleTimeout)
                expireIdleSession(session)
                coroutineContext.cancelChildren()
            }
        }
    }

    private fun expireIdleSession(session: LedgerSession) {
        val expired = synchronized(idleLock) {
            (idleSession === session).also { if (it) { idleSession = null } }
        }
        if (expired) {
            session.close()
        }
    }

    private companion object {
        const val RESULTS_BUFFER = 16
    }
}

private val LedgerProgress.targetApp: LedgerAppId?
    get() = when (this) {
        is LedgerProgress.OpeningApp -> app
        is LedgerProgress.ClosingApp -> target
        is LedgerProgress.Working -> app
        is LedgerProgress.Confirm -> app
        is LedgerProgress.VerifyAddress -> app
        LedgerProgress.Connecting,
        LedgerProgress.Pairing,
        LedgerProgress.WaitingUsb,
        LedgerProgress.UsbAccess,
        LedgerProgress.FinishPendingRequest,
        LedgerProgress.WaitingUnlock,
        LedgerProgress.Reconnecting -> null
    }
