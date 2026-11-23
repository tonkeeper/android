package com.tonapps.ledgerkit.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerPhase
import com.tonapps.ledgerkit.transport.LedgerLink
import com.tonapps.ledgerkit.transport.LedgerTransport
import com.tonapps.ledgerkit.LedgerProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

@SuppressLint("MissingPermission")
class LedgerBleTransport(
    context: Context,
    private val environment: LedgerBleEnvironment,
    private val scanner: LedgerBleScanner,
) : LedgerTransport {

    private val appContext = context.applicationContext

    override suspend fun connect(deviceId: String, onProgress: (LedgerProgress) -> Unit): LedgerLink {
        val adapter = environment.requireReadyForConnection()
        val device = try {
            adapter.getRemoteDevice(deviceId.uppercase())
        } catch (_: IllegalArgumentException) {
            throw LedgerFailureException(LedgerFailure.DeviceNotFound)
        }
        val wasBonded = device.isBondedSafely()
        val canScan = environment.canScan && environment.hasPermissions
        if (!wasBonded && canScan && scanner.awaitDevice(deviceId, SCAN_TIMEOUT) == null) {
            throw LedgerFailureException(LedgerFailure.DeviceNotFound)
        }
        val connection = LedgerBleConnection(appContext, onPairingRequested = { onProgress(LedgerProgress.Pairing) })
        try {
            connection.open(
                device = device,
                connectTimeout = if (wasBonded) { CONNECT_TIMEOUT } else { PAIRING_SETUP_TIMEOUT },
                setupTimeout = if (wasBonded) { BONDED_SETUP_TIMEOUT } else { PAIRING_SETUP_TIMEOUT },
            )
            return LedgerBleLink(deviceId, connection)
        } catch (e: CancellationException) {
            connection.release()
            if (currentCoroutineContext().isActive) {
                throw LedgerFailureException(LedgerFailure.ConnectionLost)
            }
            throw e
        } catch (e: LedgerFailureException) {
            connection.release()
            throw e.remap(wasBonded, device, connection)
        } catch (_: SecurityException) {
            connection.release()
            throw LedgerFailureException(LedgerFailure.NoPermission)
        } catch (e: Throwable) {
            connection.release()
            throw LedgerFailureException(LedgerFailure.Unexpected(e.message ?: e.javaClass.simpleName), cause = e)
        }
    }

    private fun LedgerFailureException.remap(
        wasBonded: Boolean,
        device: BluetoothDevice,
        connection: LedgerBleConnection,
    ): LedgerFailureException = when {
        wasBonded && connection.isAuthenticationRejected ->
            LedgerFailureException(LedgerFailure.PairingLost)
        !wasBonded && !device.isBondedSafely() && (connection.isBondingFailed || connection.isAuthenticationRejected) ->
            LedgerFailureException(LedgerFailure.PairingRefused)
        !wasBonded && failure == LedgerFailure.PairingRefused ->
            LedgerFailureException(LedgerFailure.ConnectionLost)
        wasBonded && failure == LedgerFailure.Timeout(LedgerPhase.Connect) ->
            LedgerFailureException(LedgerFailure.DeviceNotFound)
        else -> this
    }

    private fun BluetoothDevice.isBondedSafely(): Boolean = try {
        bondState == BluetoothDevice.BOND_BONDED
    } catch (_: SecurityException) {
        false
    }

    private companion object {
        val SCAN_TIMEOUT = 6.seconds
        val CONNECT_TIMEOUT = 15.seconds
        val BONDED_SETUP_TIMEOUT = 15.seconds
        val PAIRING_SETUP_TIMEOUT = 90.seconds
    }
}
