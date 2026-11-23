package com.tonapps.ledgerkit.transport.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException

class LedgerBleEnvironment(private val context: Context) {

    val adapter: BluetoothAdapter?
        get() = try {
            context.getSystemService(BluetoothManager::class.java)?.adapter
        } catch (_: SecurityException) {
            null
        }

    val isBluetoothSupported: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && adapter != null

    val isBluetoothEnabled: Boolean
        get() = try {
            adapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }

    val requiredPermissions: List<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    val hasPermissions: Boolean
        get() = requiredPermissions.all(::isGranted)

    val hasConnectPermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || isGranted(Manifest.permission.BLUETOOTH_CONNECT)

    val isLocationRequiredForScan: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    val isLocationEnabled: Boolean
        get() {
            val manager = context.getSystemService(LocationManager::class.java) ?: return false
            return LocationManagerCompat.isLocationEnabled(manager)
        }

    val canScan: Boolean
        get() = !isLocationRequiredForScan || isLocationEnabled

    fun check(): LedgerBleReadiness = when {
        !isBluetoothSupported -> LedgerBleReadiness.Unsupported
        !hasPermissions -> LedgerBleReadiness.MissingPermissions(requiredPermissions)
        !isBluetoothEnabled -> LedgerBleReadiness.BluetoothOff
        !canScan -> LedgerBleReadiness.LocationOff
        else -> LedgerBleReadiness.Ready
    }

    fun requireReadyForConnection(): BluetoothAdapter {
        val bluetoothAdapter = adapter
        if (!isBluetoothSupported || bluetoothAdapter == null) {
            throw LedgerFailureException(LedgerFailure.BluetoothUnavailable)
        }
        if (!hasConnectPermission) {
            throw LedgerFailureException(LedgerFailure.NoPermission)
        }
        if (!isBluetoothEnabled) {
            throw LedgerFailureException(LedgerFailure.BluetoothOff)
        }
        return bluetoothAdapter
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

sealed interface LedgerBleReadiness {
    data object Ready : LedgerBleReadiness
    data object Unsupported : LedgerBleReadiness
    data object BluetoothOff : LedgerBleReadiness
    data object LocationOff : LedgerBleReadiness
    data class MissingPermissions(val permissions: List<String>) : LedgerBleReadiness
}
