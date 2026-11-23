package com.tonapps.wallet.features.ledger.common

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import com.tonapps.ledgerkit.LedgerFailure

internal enum class LedgerSystemAction {
    EnableBluetooth,
    OpenBluetoothSettings,
    EnableLocation,
    OpenAppSettings,
    OpenLedgerLive,
}

internal val LedgerFailure.systemAction: LedgerSystemAction?
    get() = when (this) {
        LedgerFailure.BluetoothOff -> LedgerSystemAction.EnableBluetooth
        LedgerFailure.PairingLost -> LedgerSystemAction.OpenBluetoothSettings
        LedgerFailure.NoPermission -> LedgerSystemAction.OpenAppSettings
        is LedgerFailure.AppNotInstalled,
        is LedgerFailure.AppUpdateRequired -> LedgerSystemAction.OpenLedgerLive
        else -> null
    }

internal object LedgerSystemActions {

    private const val LEDGER_LIVE_PACKAGE = "com.ledger.live"
    private const val LEDGER_LIVE_STORE_URL = "https://play.google.com/store/apps/details?id=$LEDGER_LIVE_PACKAGE"

    fun perform(context: Context, action: LedgerSystemAction) {
        when (action) {
            LedgerSystemAction.EnableBluetooth -> enableBluetooth(context)
            LedgerSystemAction.OpenBluetoothSettings -> start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            LedgerSystemAction.EnableLocation -> start(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            LedgerSystemAction.OpenAppSettings -> openAppSettings(context)
            LedgerSystemAction.OpenLedgerLive -> openLedgerLive(context)
        }
    }

    private fun enableBluetooth(context: Context) {
        val requested = try {
            start(context, Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: SecurityException) {
            false
        }
        if (!requested) {
            start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    private fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        start(context, intent)
    }

    private fun openLedgerLive(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(LEDGER_LIVE_PACKAGE)
        if (launch == null || !start(context, launch)) {
            start(context, Intent(Intent.ACTION_VIEW, LEDGER_LIVE_STORE_URL.toUri()))
        }
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
