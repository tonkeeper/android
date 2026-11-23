package com.tonapps.wallet.data.ledger.connection

import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.ledgerkit.session.LedgerAppInfo
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.LedgerRoutingTransport
import com.tonapps.ledgerkit.transport.ble.LedgerBleDevice
import com.tonapps.ledgerkit.transport.ble.LedgerBleEnvironment
import com.tonapps.ledgerkit.transport.ble.LedgerBleReadiness
import com.tonapps.ledgerkit.transport.ble.LedgerBleScanner
import com.tonapps.ledgerkit.transport.usb.LedgerUsbEnvironment
import com.tonapps.ledgerkit.transport.usb.LedgerUsbIds
import com.tonapps.wallet.data.ledger.analytics.LedgerIssueSite
import com.tonapps.wallet.data.ledger.analytics.reportingLedgerIssues
import com.tonapps.wallet.data.ledger.lab.LedgerLab
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface LedgerConnectionInteractor {

    fun status(walletId: String, policy: LedgerScanPolicy): Flow<LedgerConnectionStatus>

    fun linkKind(deviceId: String): Flow<LedgerLinkKind>

    fun activity(walletId: String): Flow<List<LedgerActivity>>

    suspend fun checkConnection(walletId: String): LedgerAppInfo

    fun cancelCheck()
}

internal interface LedgerRadio {

    fun readiness(): LedgerBleReadiness

    suspend fun awaitScanSlot()

    fun scan(): Flow<List<LedgerBleDevice>>

    fun isAlwaysPresent(deviceId: String): Boolean

    fun usbAttached(): Flow<Boolean>

    fun routesToUsb(deviceId: String): Boolean
}

internal class LedgerBleRadio(
    private val environment: LedgerBleEnvironment,
    private val scanner: LedgerBleScanner,
    private val lab: LedgerLab,
    private val usb: LedgerUsbEnvironment,
    private val router: LedgerRoutingTransport,
) : LedgerRadio {

    override fun readiness(): LedgerBleReadiness = environment.check()

    override suspend fun awaitScanSlot() = scanner.awaitStartSlot(keepFree = USER_SCAN_STARTS)

    override fun scan(): Flow<List<LedgerBleDevice>> = scanner.scan(lowPower = true, startReserved = true)

    override fun isAlwaysPresent(deviceId: String): Boolean = lab.devices().any { it.address.equals(deviceId, ignoreCase = true) }

    override fun usbAttached(): Flow<Boolean> = usb.observe().map { it.isNotEmpty() }.distinctUntilChanged()

    override fun routesToUsb(deviceId: String): Boolean = router.routesToUsb(deviceId)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class LedgerConnectionInteractorImpl(
    private val controller: LedgerController,
    private val accountRepository: McAccountRepository,
    private val activityLog: LedgerActivityLog,
    private val radio: LedgerRadio,
) : LedgerConnectionInteractor {

    private val LedgerScanPolicy.timings: ScanTimings
        get() = when (this) {
            LedgerScanPolicy.Detailed -> ScanTimings(window = 12.seconds, nearbyRecheck = 20.seconds, retryPause = 5.seconds)
        }

    override fun status(walletId: String, policy: LedgerScanPolicy): Flow<LedgerConnectionStatus> =
        deviceId(walletId).flatMapLatest { deviceId -> deviceStatus(deviceId, policy) }

    override fun linkKind(deviceId: String): Flow<LedgerLinkKind> = combine(controller.link, radio.usbAttached()) { link, _ ->
        link?.takeIf { it.deviceId.equals(deviceId, ignoreCase = true) }?.kind
            ?: if (radio.routesToUsb(deviceId)) { LedgerLinkKind.Usb } else { LedgerLinkKind.Bluetooth }
    }.distinctUntilChanged()

    override fun activity(walletId: String): Flow<List<LedgerActivity>> = deviceId(walletId).flatMapLatest { deviceId ->
        activityLog.entries.map { list -> list.filter { it.deviceId.equals(deviceId, ignoreCase = true) } }
    }

    private fun deviceId(walletId: String): Flow<String> = accountRepository.refreshTrigger
        .map { accountRepository.getHardwareDescriptor(walletId)?.deviceId }
        .filterNotNull()
        .distinctUntilChanged()

    override suspend fun checkConnection(walletId: String): LedgerAppInfo = reportingLedgerIssues(LedgerIssueSite.Connection) {
        val descriptor = accountRepository.getHardwareDescriptor(walletId)
            ?: throw LedgerFailureException(LedgerFailure.DeviceNotFound)
        val request = LedgerRequest(descriptor.deviceId, descriptor.deviceName, LedgerPurpose.Connect, walletId)
        coroutineScope {
            val dismissFailure = launch {
                controller.state.first { it is LedgerOperationState.Failed && it.request == request }
                controller.dismiss()
            }
            try {
                controller.run(request) { session.currentApp() }
            } finally {
                dismissFailure.cancel()
            }
        }
    }

    override fun cancelCheck() {
        val active = controller.state.value as? LedgerOperationState.Active ?: return
        if (active.request.purpose == LedgerPurpose.Connect) {
            controller.cancel()
        }
    }

    internal fun deviceStatus(deviceId: String, policy: LedgerScanPolicy): Flow<LedgerConnectionStatus> {
        var wasLinked = false
        return linkStatus(deviceId)
            .flatMapLatest { link ->
                if (link != null) {
                    wasLinked = true
                    flowOf(link)
                } else {
                    presence(deviceId, policy.timings, assumeNearby = wasLinked.also { wasLinked = false })
                }
            }
            .distinctUntilChanged()
    }

    private fun linkStatus(deviceId: String): Flow<LedgerConnectionStatus?> =
        combine(controller.state, controller.link) { state, link ->
            val active = state as? LedgerOperationState.Active
            if (active != null && active.request.deviceId.equals(deviceId, ignoreCase = true)) {
                LedgerConnectionStatus.Busy(active.request.purpose, active.progress)
            } else {
                link?.takeIf { it.deviceId.equals(deviceId, ignoreCase = true) }
                    ?.let { LedgerConnectionStatus.Connected(it.app?.takeUnless(LedgerAppInfo::isDashboard)?.appId, isUsb = it.kind == LedgerLinkKind.Usb) }
            }
        }.distinctUntilChanged()

    private fun presence(deviceId: String, timings: ScanTimings, assumeNearby: Boolean): Flow<LedgerConnectionStatus> =
        radio.usbAttached().flatMapLatest { attached ->
            when {
                attached -> flowOf(LedgerConnectionStatus.UsbConnected)
                LedgerUsbIds.isUsb(deviceId) -> flowOf(LedgerConnectionStatus.UsbDisconnected)
                else -> bluetoothPresence(deviceId, timings, assumeNearby)
            }
        }

    private fun bluetoothPresence(deviceId: String, timings: ScanTimings, assumeNearby: Boolean): Flow<LedgerConnectionStatus> = flow {
        var last: LedgerConnectionStatus? = null
        if (assumeNearby && radio.readiness() == LedgerBleReadiness.Ready) {
            last = LedgerConnectionStatus.Nearby
            emit(last)
        }
        while (true) {
            if (radio.isAlwaysPresent(deviceId)) {
                emit(LedgerConnectionStatus.Nearby)
                delay(READINESS_POLL)
                continue
            }
            val readiness = radio.readiness()
            if (readiness != LedgerBleReadiness.Ready) {
                last = readiness.toStatus()
                emit(last)
                delay(READINESS_POLL)
                continue
            }
            if (last != LedgerConnectionStatus.Nearby && last != LedgerConnectionStatus.NotFound) {
                emit(LedgerConnectionStatus.Searching)
            }
            last = scanOnce(deviceId, timings.window)
            emit(last)
            delay(if (last == LedgerConnectionStatus.Nearby) { timings.nearbyRecheck } else { timings.retryPause })
        }
    }

    private suspend fun scanOnce(deviceId: String, window: Duration): LedgerConnectionStatus = try {
        radio.awaitScanSlot()
        val found = withTimeoutOrNull(window) {
            radio.scan().first { devices -> devices.any { it.address.equals(deviceId, ignoreCase = true) } }
        }
        if (found != null) {
            LedgerConnectionStatus.Nearby
        } else {
            LedgerConnectionStatus.NotFound
        }
    } catch (e: LedgerFailureException) {
        e.failure.toStatus()
    }

    private fun LedgerBleReadiness.toStatus(): LedgerConnectionStatus = when (this) {
        LedgerBleReadiness.Ready -> LedgerConnectionStatus.Searching
        LedgerBleReadiness.Unsupported -> LedgerConnectionStatus.Unsupported
        LedgerBleReadiness.BluetoothOff -> LedgerConnectionStatus.BluetoothOff
        LedgerBleReadiness.LocationOff -> LedgerConnectionStatus.LocationOff
        is LedgerBleReadiness.MissingPermissions -> LedgerConnectionStatus.NoPermission
    }

    private fun LedgerFailure.toStatus(): LedgerConnectionStatus = when (this) {
        LedgerFailure.BluetoothOff -> LedgerConnectionStatus.BluetoothOff
        LedgerFailure.NoPermission -> LedgerConnectionStatus.NoPermission
        LedgerFailure.BluetoothUnavailable -> LedgerConnectionStatus.Unsupported
        else -> LedgerConnectionStatus.NotFound
    }

    private class ScanTimings(val window: Duration, val nearbyRecheck: Duration, val retryPause: Duration)

    private companion object {
        val READINESS_POLL = 2.seconds
    }
}

private const val USER_SCAN_STARTS = 1
