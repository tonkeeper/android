package com.tonapps.deposit.multicoin.moonpay

import com.tonapps.async.Async
import com.tonapps.deposit.multicoin.moonpay.api.ElapsedRealtimeTimeSource
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import com.tonapps.deposit.multicoin.catchingResult
import com.tonapps.deposit.multicoin.moonpay.api.parseMoonPayInstantMs
import com.tonapps.deposit.multicoin.moonpay.crypto.MoonPayCredentials
import com.tonapps.deposit.multicoin.moonpay.crypto.MoonPayKeyPair
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayConnectionResult
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayConnectionStatus
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrame
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameCompletion
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameError
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameEvent
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrames
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTheme
import com.tonapps.log.L
import com.tonapps.wallet.api.API
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

data class MoonPayConnection(
    val environment: MoonPayEnvironment,
    val credentials: MoonPayCredentials,
)

sealed class MoonPayConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class Session(cause: Throwable) : MoonPayConnectionException("MoonPay session could not be created", cause)

    class Rejected(val status: MoonPayConnectionStatus, val signInOffered: Boolean = true) :
        MoonPayConnectionException("MoonPay connection is ${status.value}")

    class Remote(val error: MoonPayFrameError) :
        MoonPayConnectionException("MoonPay connection failed: ${error.code} ${error.message}")

    class TimedOut : MoonPayConnectionException("MoonPay connection check timed out")

    class Cancelled : MoonPayConnectionException("MoonPay connection was closed by the user")
}

// Credentials live in memory only. One connection attempt runs at a time; callers that come
// while it runs get its result, a failure included. A caller that may not sign in gets
// Rejected instead of the connect frame, unless it joined an attempt that already shows it.
class MoonPayConnectionRepository(
    private val api: API,
    private val environmentProvider: MoonPayEnvironmentProvider,
    private val frames: MoonPayFrames,
    private val timeSource: TimeSource.WithComparableMarks = ElapsedRealtimeTimeSource,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    private val ioContext: CoroutineContext = Async.Io,
) {

    private class Cached(val connection: MoonPayConnection, val validUntil: ComparableTimeMark)

    // `result` completes with null when the caller running the attempt was cancelled.
    private class Attempt(val environment: MoonPayEnvironment, val interactive: Boolean, val generation: Int) {
        val result = CompletableDeferred<Result<MoonPayConnection>?>()
    }

    private sealed interface Step {
        data class Ready(val connection: MoonPayConnection) : Step
        data class Join(val attempt: Attempt) : Step
        data class Run(val attempt: Attempt) : Step
    }

    private val lock = Any()

    private var cached: Cached? = null

    private var attempt: Attempt? = null

    private var generation = 0

    suspend fun ensureConnected(theme: MoonPayTheme?, interactive: Boolean): Result<MoonPayConnection> {
        while (true) {
            val result = when (val step = nextStep(interactive)) {
                is Step.Ready -> return Result.success(step.connection)
                is Step.Join -> {
                    val joined = step.attempt.result.await()
                    val retry = joined == null || (joined.isFailure && interactive && !step.attempt.interactive)
                    if (retry) {
                        null
                    } else {
                        joined
                    }
                }
                is Step.Run -> run(step, theme)
            }
            if (result != null) {
                return result
            }
        }
    }

    // An attempt already running neither caches its credentials nor takes new callers.
    fun invalidate() {
        synchronized(lock) {
            cached = null
            generation++
        }
    }

    // Drops the credentials only while they are the refused ones: newer credentials, or an attempt running for
    // them, are another caller's and stay.
    fun invalidate(refused: MoonPayConnection) {
        synchronized(lock) {
            if (cached?.connection?.credentials?.accessToken == refused.credentials.accessToken) {
                cached = null
            }
        }
    }

    private fun nextStep(interactive: Boolean): Step = synchronized(lock) {
        val environment = environment()
        val valid = cached?.takeIf { it.connection.environment == environment && it.validUntil.hasNotPassedNow() }
        val running = attempt?.takeIf { it.generation == generation && it.environment == environment }
        when {
            valid != null -> Step.Ready(valid.connection)
            running != null -> Step.Join(running)
            else -> Step.Run(Attempt(environment, interactive, generation).also { attempt = it })
        }
    }

    private suspend fun run(step: Step.Run, theme: MoonPayTheme?): Result<MoonPayConnection> {
        val running = step.attempt
        val result = try {
            catchingResult {
                MoonPayConnection(running.environment, connect(running.environment, theme, running.interactive))
            }
        } catch (e: CancellationException) {
            finish(running, null)
            throw e
        }
        finish(running, result)
        return result
    }

    private fun finish(running: Attempt, result: Result<MoonPayConnection>?) {
        synchronized(lock) {
            val connection = result?.getOrNull()
            if (connection != null && running.generation == generation) {
                cached = validUntil(connection.credentials)?.let { Cached(connection, it) }
            }
            if (attempt === running) {
                attempt = null
            }
        }
        running.result.complete(result)
    }

    private fun environment(): MoonPayEnvironment {
        return if (environmentProvider.useSandbox()) {
            MoonPayEnvironment.Sandbox
        } else {
            MoonPayEnvironment.Production
        }
    }

    private suspend fun connect(
        environment: MoonPayEnvironment,
        theme: MoonPayTheme?,
        interactive: Boolean,
    ): MoonPayCredentials {
        val sessionToken = createSession(environment)
        val keyPair = MoonPayKeyPair()
        val frame = MoonPayFrame.CheckConnection(sessionToken = sessionToken, publicKeyHex = keyPair.publicKeyHex)
        val (result, credentials) = withTimeoutOrNull(CHECK_CONNECTION_TIMEOUT) {
            awaitConnection(frame, environment, keyPair)
        } ?: throw MoonPayConnectionException.TimedOut()
        L.i("MoonPay connection: ${result.status.value}, reason ${result.reason}, mismatch ${result.mismatch}")
        return when (result.status) {
            MoonPayConnectionStatus.Active -> credentials ?: throw MoonPayConnectionException.Rejected(result.status)
            MoonPayConnectionStatus.ConnectionRequired,
            MoonPayConnectionStatus.TermsAcceptanceRequired,
            -> {
                val clientToken = credentials?.clientToken
                if (clientToken == null || !interactive) {
                    throw MoonPayConnectionException.Rejected(result.status, signInOffered = clientToken != null)
                }
                connectInteractively(environment, clientToken, theme)
            }
            MoonPayConnectionStatus.Pending,
            MoonPayConnectionStatus.Unavailable,
            MoonPayConnectionStatus.Failed,
            -> throw MoonPayConnectionException.Rejected(result.status)
        }
    }

    private suspend fun connectInteractively(
        environment: MoonPayEnvironment,
        clientToken: String,
        theme: MoonPayTheme?,
    ): MoonPayCredentials {
        val keyPair = MoonPayKeyPair()
        val frame = MoonPayFrame.Connect(clientToken = clientToken, publicKeyHex = keyPair.publicKeyHex, theme = theme)
        val (result, credentials) = awaitConnection(frame, environment, keyPair)
        L.i("MoonPay connect: ${result.status.value}, reason ${result.reason}, mismatch ${result.mismatch}")
        if (result.status != MoonPayConnectionStatus.Active || credentials == null) {
            throw MoonPayConnectionException.Rejected(result.status)
        }
        return credentials
    }

    private suspend fun awaitConnection(
        frame: MoonPayFrame,
        environment: MoonPayEnvironment,
        keyPair: MoonPayKeyPair,
    ): Pair<MoonPayConnectionResult, MoonPayCredentials?> {
        val result = frames.run(frame, environment)
            .mapNotNull(::connectionResult)
            .firstOrNull()
            ?: throw MoonPayConnectionException.Cancelled()
        return result to result.encryptedCredentials?.let(keyPair::decrypt)
    }

    private fun connectionResult(event: MoonPayFrameEvent): MoonPayConnectionResult? {
        return when (event) {
            is MoonPayFrameEvent.Complete -> (event.completion as? MoonPayFrameCompletion.Connection)?.result
            is MoonPayFrameEvent.Error -> throw MoonPayConnectionException.Remote(event.error)
            is MoonPayFrameEvent.Handshake,
            is MoonPayFrameEvent.Ready,
            is MoonPayFrameEvent.TransactionCreated,
            is MoonPayFrameEvent.Challenge,
            is MoonPayFrameEvent.Cancelled,
            is MoonPayFrameEvent.ButtonPressed,
            is MoonPayFrameEvent.Resolved,
            is MoonPayFrameEvent.Unknown,
            -> null
        }
    }

    private suspend fun createSession(environment: MoonPayEnvironment): String = withContext(ioContext) {
        try {
            api.exchange.moonpay.createMoonPaySession(sandbox = environment == MoonPayEnvironment.Sandbox).sessionToken
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw MoonPayConnectionException.Session(e)
        }
    }

    // The credentials carry no server time, so the wall clock is read once, here.
    private fun validUntil(credentials: MoonPayCredentials): ComparableTimeMark? {
        val expiresAtMs = parseMoonPayInstantMs(credentials.expiresAt) ?: return null
        return timeSource.markNow() + (expiresAtMs - wallClockMs()).milliseconds - EXPIRY_MARGIN
    }

    private companion object {
        val CHECK_CONNECTION_TIMEOUT = 20.seconds
        val EXPIRY_MARGIN = 1.minutes
    }
}
