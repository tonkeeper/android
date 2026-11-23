package com.tonapps.ledgerkit.transport.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import com.tonapps.ledgerkit.transport.LedgerModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

data class LedgerUsbDevice(
    val name: String,
    val model: LedgerModel?,
) {

    val deviceId: String
        get() = LedgerUsbIds.deviceId(model)

    val displayName: String
        get() = model?.productName ?: DEFAULT_NAME

    private companion object {
        const val DEFAULT_NAME = "Ledger"
    }
}

class LedgerUsbEnvironment(
    context: Context,
    private val extraDevices: () -> List<LedgerUsbDevice> = { emptyList() },
    private val refreshInterval: Duration? = null,
) {

    private val appContext = context.applicationContext

    private val manager: UsbManager?
        get() = appContext.getSystemService(UsbManager::class.java)

    val isAttached: Boolean
        get() = devices().isNotEmpty()

    fun devices(): List<LedgerUsbDevice> = extraDevices() + usbDevices().map { it.toLedgerDevice() }

    fun observe(): Flow<List<LedgerUsbDevice>> = watch(::devices)

    internal suspend fun awaitUsbDevice(): UsbDevice = watch(::usbDevices).first { it.isNotEmpty() }.first()

    internal fun findUsbDevice(): UsbDevice? = usbDevices().firstOrNull()

    internal fun isPresent(deviceName: String): Boolean = usbDevices().any { it.deviceName == deviceName }

    internal fun hasPermission(device: UsbDevice): Boolean = try {
        manager?.hasPermission(device) == true
    } catch (_: SecurityException) {
        false
    }

    internal suspend fun awaitPermission(device: UsbDevice, timeout: Duration): Boolean {
        val deadline = TimeSource.Monotonic.markNow() + timeout
        while (!hasPermission(device)) {
            if (deadline.hasPassedNow() || !isPresent(device.deviceName)) {
                return false
            }
            delay(PERMISSION_POLL)
        }
        return true
    }

    internal suspend fun requestPermission(device: UsbDevice): Boolean {
        val usbManager = manager ?: return false
        val result = CompletableDeferred<Boolean>()
        val action = appContext.packageName + PERMISSION_ACTION_SUFFIX
        val permissionReceiver = receiver { intent ->
            result.complete(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
        }
        val detachReceiver = receiver {
            if (!isPresent(device.deviceName)) {
                result.complete(false)
            }
        }
        ContextCompat.registerReceiver(appContext, permissionReceiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        register(detachReceiver, UsbManager.ACTION_USB_DEVICE_DETACHED)
        return try {
            val intent = Intent(action).setPackage(appContext.packageName)
            val pending = PendingIntentCompat.getBroadcast(appContext, PERMISSION_REQUEST_CODE, intent, 0, true)
            usbManager.requestPermission(device, pending)
            result.await() || hasPermission(device)
        } catch (_: SecurityException) {
            false
        } finally {
            unregister(permissionReceiver)
            unregister(detachReceiver)
        }
    }

    internal fun onDetached(deviceName: String, block: () -> Unit): () -> Unit {
        val detachReceiver = receiver {
            if (!isPresent(deviceName)) {
                block()
            }
        }
        register(detachReceiver, UsbManager.ACTION_USB_DEVICE_DETACHED)
        return { unregister(detachReceiver) }
    }

    internal fun open(device: UsbDevice): UsbDeviceConnection? = try {
        manager?.openDevice(device)
    } catch (_: SecurityException) {
        null
    }

    private fun <T> watch(snapshot: () -> T): Flow<T> = callbackFlow {
        val changes = receiver { trySend(snapshot()) }
        register(changes, UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED)
        trySend(snapshot())
        val refresh = refreshInterval?.let { interval ->
            launch {
                while (true) {
                    delay(interval)
                    trySend(snapshot())
                }
            }
        }
        awaitClose {
            refresh?.cancel()
            unregister(changes)
        }
    }.distinctUntilChanged()

    private fun usbDevices(): List<UsbDevice> = try {
        manager?.deviceList?.values.orEmpty().filter { it.vendorId == LedgerModel.USB_VENDOR_ID }
    } catch (_: SecurityException) {
        emptyList()
    }

    private fun UsbDevice.toLedgerDevice() = LedgerUsbDevice(deviceName, LedgerModel.fromUsbProductId(productId))

    private fun receiver(handle: (Intent) -> Unit) = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = handle(intent)
    }

    private fun register(receiver: BroadcastReceiver, vararg actions: String) {
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun unregister(receiver: BroadcastReceiver) {
        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    private companion object {
        const val PERMISSION_ACTION_SUFFIX = ".LEDGER_USB_PERMISSION"
        const val PERMISSION_REQUEST_CODE = 0x2c97
        val PERMISSION_POLL = 200.milliseconds
    }
}
