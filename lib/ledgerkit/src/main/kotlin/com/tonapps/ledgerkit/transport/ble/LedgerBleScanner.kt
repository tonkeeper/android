package com.tonapps.ledgerkit.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.os.SystemClock
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.transport.LedgerModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class LedgerBleDevice(
    val address: String,
    val name: String,
    val model: LedgerModel,
    val rssi: Int,
    val lastSeenAt: Long,
)

@SuppressLint("MissingPermission")
class LedgerBleScanner(
    private val environment: LedgerBleEnvironment,
    private val extraDevices: () -> List<LedgerBleDevice> = { emptyList() },
) {

    private val recentlySeen = ConcurrentHashMap<String, LedgerBleDevice>()
    private val startBudget = ScanStartBudget(MAX_SCAN_STARTS, SCAN_START_WINDOW_MS, SystemClock::elapsedRealtime)

    suspend fun awaitStartSlot(keepFree: Int = 0) {
        while (true) {
            val wait = startBudget.reserve(keepFree)
            if (wait == 0L) {
                return
            }
            delay(wait)
        }
    }

    fun scan(
        staleAfter: Duration = STALE_AFTER,
        lowPower: Boolean = false,
        startReserved: Boolean = false,
    ): Flow<List<LedgerBleDevice>> = callbackFlow {
        val adapter = environment.requireReadyForConnection()
        val scanner = adapter.bluetoothLeScanner ?: throw LedgerFailureException(LedgerFailure.BluetoothOff)
        if (!startReserved) {
            awaitStartSlot()
        }
        val devices = linkedMapOf<String, LedgerBleDevice>()

        fun publish() {
            val snapshot = synchronized(devices) { devices.values.toList() }
            val extra = extraDevices()
            trySend(extra + snapshot.filter { device -> extra.none { it.address == device.address } })
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.toLedgerDevice() ?: return
                remember(device)
                synchronized(devices) { devices[device.address] = device }
                publish()
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                val found = results.mapNotNull { it.toLedgerDevice() }
                found.forEach(::remember)
                synchronized(devices) { found.forEach { devices[it.address] = it } }
                publish()
            }

            override fun onScanFailed(errorCode: Int) {
                close(LedgerFailureException(LedgerFailure.ScanUnavailable))
            }
        }

        val filters = LedgerModel.bluetoothServices.map { service ->
            ScanFilter.Builder().setServiceUuid(ParcelUuid(service.service)).build()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(if (lowPower) { ScanSettings.SCAN_MODE_BALANCED } else { ScanSettings.SCAN_MODE_LOW_LATENCY })
            .build()
        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: SecurityException) {
            throw LedgerFailureException(LedgerFailure.NoPermission)
        }
        publish()

        val pruning = launch {
            while (true) {
                delay(PRUNE_INTERVAL)
                val threshold = SystemClock.elapsedRealtime() - staleAfter.inWholeMilliseconds
                val removed = synchronized(devices) { devices.values.removeAll { it.lastSeenAt < threshold } }
                if (removed) {
                    publish()
                }
            }
        }

        awaitClose {
            pruning.cancel()
            try {
                scanner.stopScan(callback)
            } catch (_: Throwable) {
            }
        }
    }

    suspend fun awaitDevice(address: String, timeout: Duration): LedgerBleDevice? {
        val threshold = SystemClock.elapsedRealtime() - RECENTLY_SEEN.inWholeMilliseconds
        recentlySeen[address.uppercase()]?.takeIf { it.lastSeenAt >= threshold }?.let { return it }
        environment.requireReadyForConnection()
        awaitStartSlot()
        return withTimeoutOrNull(timeout) {
            scan(startReserved = true).first { devices -> devices.any { it.address.equals(address, ignoreCase = true) } }
                .first { it.address.equals(address, ignoreCase = true) }
        }
    }

    private fun remember(device: LedgerBleDevice) {
        recentlySeen[device.address.uppercase()] = device
    }

    private fun ScanResult.toLedgerDevice(): LedgerBleDevice? {
        val uuids = scanRecord?.serviceUuids.orEmpty().map { it.uuid }
        val model = uuids.firstNotNullOfOrNull(LedgerModel::fromServiceUuid) ?: return null
        val advertisedName = try {
            scanRecord?.deviceName ?: device.name
        } catch (_: SecurityException) {
            null
        }
        return LedgerBleDevice(
            address = device.address,
            name = advertisedName?.takeIf { it.isNotBlank() } ?: model.productName,
            model = model,
            rssi = rssi,
            lastSeenAt = SystemClock.elapsedRealtime(),
        )
    }

    private companion object {
        val STALE_AFTER = 8.seconds
        val PRUNE_INTERVAL = 1.seconds
        val RECENTLY_SEEN = 30.seconds
        const val MAX_SCAN_STARTS = 4
        const val SCAN_START_WINDOW_MS = 30_000L
    }
}
