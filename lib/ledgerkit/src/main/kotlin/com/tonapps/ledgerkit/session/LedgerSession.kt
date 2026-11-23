package com.tonapps.ledgerkit.session

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerPhase
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.apdu.ApduChannel
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.apdu.StatusWord
import com.tonapps.ledgerkit.transport.LedgerLink
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.LedgerTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class LedgerSession(
    private val transport: LedgerTransport,
    val deviceId: String,
    private val timings: LedgerSessionTimings = LedgerSessionTimings(),
) : ApduChannel {

    var reporter: (LedgerProgress) -> Unit = {}

    var onLinkChanged: () -> Unit = {}

    private var currentLink: LedgerLink? = null
        set(value) {
            field = value
            onLinkChanged()
        }

    private var knownApp: LedgerAppInfo? = null
        set(value) {
            field = value
            onLinkChanged()
        }

    @Volatile
    var lockEvents: Int = 0
        private set

    val isOpen: Boolean
        get() = currentLink?.isConnected == true

    val model: LedgerModel?
        get() = currentLink?.model

    val linkKind: LedgerLinkKind
        get() = currentLink?.kind ?: LedgerLinkKind.Bluetooth

    val currentLinkKind: LedgerLinkKind?
        get() = currentLink?.kind

    val lastKnownApp: LedgerAppInfo?
        get() = knownApp?.takeIf { isOpen }

    suspend fun link(): LedgerLink {
        currentLink?.takeIf { it.isConnected }?.let { return it }
        currentLink?.close()
        currentLink = null
        knownApp = null
        reporter(LedgerProgress.Connecting)
        val link = transport.connect(deviceId, reporter)
        currentLink = link
        return link
    }

    override suspend fun exchange(apdu: ByteArray, timeout: Duration?): ApduResponse {
        val link = link()
        return try {
            link.exchange(apdu, timeout)
        } catch (e: LedgerFailureException) {
            if (e.failure is LedgerFailure.ConnectionLost || e.failure is LedgerFailure.Timeout || e.failure is LedgerFailure.Unexpected) {
                dropLink()
            }
            throw e
        }
    }

    suspend fun currentApp(): LedgerAppInfo {
        val deadline = TimeSource.Monotonic.markNow() + timings.unlockBudget
        var reported: LedgerProgress? = null
        var staleReplies = 0
        while (true) {
            val response = exchange(Bolos.appAndVersionCommand(), timings.nonInteractiveTimeout)
            val waiting = when (response.statusWord) {
                in LOCKED_STATUS_WORDS -> LedgerProgress.WaitingUnlock
                in BUSY_STATUS_WORDS -> LedgerProgress.FinishPendingRequest
                else -> null
            }
            if (waiting != null) {
                if (deadline.hasPassedNow()) {
                    throw LedgerFailureException(if (waiting == LedgerProgress.WaitingUnlock) { LedgerFailure.DeviceLocked } else { LedgerFailure.Busy })
                }
                if (reported != waiting) {
                    if (waiting == LedgerProgress.WaitingUnlock) {
                        lockEvents++
                    }
                    reporter(waiting)
                    reported = waiting
                }
                delay(timings.unlockPollInterval)
                continue
            }
            if (response.isOk && !Bolos.isAppAndVersion(response) && staleReplies < MAX_STALE_REPLIES) {
                staleReplies++
                continue
            }
            return Bolos.parseAppAndVersion(response).also { knownApp = it }
        }
    }

    suspend fun ensureApp(target: LedgerAppId): LedgerAppInfo {
        knownApp?.takeIf { it.name == target.openName && isOpen }?.let { return it }
        var switches = 0
        var reconnects = 0
        var lastSeen = ""
        while (true) {
            val info = try {
                currentApp()
            } catch (e: LedgerFailureException) {
                if (switches == 0 || e.failure != LedgerFailure.ConnectionLost || reconnects >= MAX_SWITCH_RECONNECTS) {
                    throw e
                }
                reconnects++
                reconnectAfterSwitch()
                continue
            }
            lastSeen = info.name
            if (info.name == target.openName) {
                return info
            }
            if (switches >= MAX_APP_SWITCH_ATTEMPTS) {
                throw LedgerFailureException(LedgerFailure.WrongAppOpen(lastSeen, target))
            }
            switches++
            if (info.isDashboard) {
                openApp(target)
            } else {
                quitApp(info.name, target)
            }
        }
    }

    fun forgetCurrentApp() {
        knownApp = null
    }

    suspend fun awaitDisconnected() {
        currentLink?.awaitDisconnected()
    }

    private suspend fun openApp(target: LedgerAppId) {
        reporter(LedgerProgress.OpeningApp(target))
        val response = switchExchange(Bolos.openAppCommand(target), timeout = null) ?: return
        if (!response.isOk) {
            throw LedgerFailureException(Bolos.openAppFailure(response.statusWord, target))
        }
        knownApp = null
        awaitAppSwitch()
    }

    private suspend fun quitApp(openAppName: String, target: LedgerAppId) {
        reporter(LedgerProgress.ClosingApp(openAppName, target))
        val response = switchExchange(Bolos.quitAppCommand(), timings.nonInteractiveTimeout) ?: return
        if (!response.isOk) {
            throw LedgerFailureException(LedgerFailure.WrongAppOpen(openAppName, target))
        }
        knownApp = null
        awaitAppSwitch()
    }

    private suspend fun switchExchange(apdu: ByteArray, timeout: Duration?): ApduResponse? = try {
        exchange(apdu, timeout)
    } catch (e: LedgerFailureException) {
        if (e.failure != LedgerFailure.ConnectionLost) {
            throw e
        }
        reconnectAfterSwitch()
        null
    }

    private suspend fun reconnectAfterSwitch() {
        knownApp = null
        reporter(LedgerProgress.Reconnecting)
        dropLink()
        reconnect()
    }

    private suspend fun awaitAppSwitch() {
        val link = currentLink ?: return
        val dropped = withTimeoutOrNull(timings.disconnectGrace) {
            link.awaitDisconnected()
            true
        } ?: false
        if (!dropped) {
            delay(timings.appLaunchSettle)
            return
        }
        reporter(LedgerProgress.Reconnecting)
        dropLink()
        reconnect()
    }

    private suspend fun reconnect() {
        val deadline = TimeSource.Monotonic.markNow() + timings.reconnectBudget
        var lastFailure: LedgerFailure = LedgerFailure.ConnectionLost
        while (!deadline.hasPassedNow()) {
            try {
                val link = transport.connect(deviceId)
                currentLink = link
                return
            } catch (e: LedgerFailureException) {
                lastFailure = e.failure
                if (!e.failure.isTransient()) {
                    throw e
                }
                delay(timings.reconnectRetryDelay)
            }
        }
        throw LedgerFailureException(
            if (lastFailure == LedgerFailure.DeviceNotFound) { LedgerFailure.Timeout(LedgerPhase.Reconnect) } else { lastFailure },
        )
    }

    private fun dropLink() {
        currentLink?.close()
        currentLink = null
        knownApp = null
    }

    fun close() {
        dropLink()
    }

    private fun LedgerFailure.isTransient(): Boolean = this == LedgerFailure.DeviceNotFound ||
        this == LedgerFailure.ConnectionLost ||
        this is LedgerFailure.Timeout

    private companion object {
        const val MAX_APP_SWITCH_ATTEMPTS = 4
        const val MAX_SWITCH_RECONNECTS = 4
        const val MAX_STALE_REPLIES = 3
        val BUSY_STATUS_WORDS = setOf(StatusWord.DEVICE_BUSY)
        val LOCKED_STATUS_WORDS = setOf(StatusWord.DEVICE_LOCKED, StatusWord.LOCKED_ALT, StatusWord.LOCKED_PIN, StatusWord.SECURITY_STATUS_NOT_SATISFIED)
    }
}

data class LedgerSessionTimings(
    val nonInteractiveTimeout: Duration = 10.seconds,
    val unlockBudget: Duration = 5.minutes,
    val unlockPollInterval: Duration = 1500.milliseconds,
    val disconnectGrace: Duration = 2500.milliseconds,
    val appLaunchSettle: Duration = 500.milliseconds,
    val reconnectBudget: Duration = 25.seconds,
    val reconnectRetryDelay: Duration = 1.seconds,
)
