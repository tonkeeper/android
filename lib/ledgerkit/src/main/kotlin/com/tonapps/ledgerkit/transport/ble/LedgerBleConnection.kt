package com.tonapps.ledgerkit.transport.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.tonapps.lib.ledgerkit.BuildConfig
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerPhase
import com.tonapps.ledgerkit.apdu.Apdu
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.transport.LedgerLink
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.log.L
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectionPriorityRequest
import no.nordicsemi.android.ble.observer.BondingObserver
import no.nordicsemi.android.ble.callback.FailCallback
import no.nordicsemi.android.ble.data.DataSplitter
import no.nordicsemi.android.ble.exception.BluetoothDisabledException
import no.nordicsemi.android.ble.exception.DeviceDisconnectedException
import no.nordicsemi.android.ble.exception.InvalidRequestException
import no.nordicsemi.android.ble.exception.RequestFailedException
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.ble.observer.ConnectionObserver
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class LedgerBleConnection(
    context: Context,
    private val onPairingRequested: () -> Unit = {},
) : BleManager(context) {

    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    private var matchedModel: LedgerModel? = null

    @Volatile
    private var frameSize = LedgerBleFraming.MIN_FRAME_SIZE

    @Volatile
    private var inFlight: CompletableDeferred<ByteArray>? = null

    @Volatile
    private var closed = false

    @Volatile
    var isBondingFailed = false
        private set

    @Volatile
    var isAuthenticationRejected = false
        private set

    private val reassembler = LedgerBleReassembler()
    private val notificationsReady = CompletableDeferred<Unit>()
    private val handshakeReply = CompletableDeferred<Int?>()
    private val disconnected = CompletableDeferred<Unit>()
    private val exchangeMutex = Mutex()

    val model: LedgerModel
        get() = matchedModel ?: LedgerModel.NanoX

    val isLinkUp: Boolean
        get() = !closed && !disconnected.isCompleted && isReady

    init {
        setBondingObserver(object : BondingObserver {
            override fun onBondingRequired(device: BluetoothDevice) = onPairingRequested()
            override fun onBonded(device: BluetoothDevice) = Unit
            override fun onBondingFailed(device: BluetoothDevice) {
                isBondingFailed = true
            }
        })
        setConnectionObserver(object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) = Unit
            override fun onDeviceConnected(device: BluetoothDevice) = Unit
            override fun onDeviceReady(device: BluetoothDevice) = Unit
            override fun onDeviceDisconnecting(device: BluetoothDevice) = Unit

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                onLinkLost()
            }

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                onLinkLost()
            }
        })
    }

    override fun getMinLogPriority(): Int = if (BuildConfig.DEBUG) { Log.INFO } else { Log.ASSERT }

    override fun log(priority: Int, message: String) {
        if (BuildConfig.DEBUG && priority >= Log.INFO) {
            L.d("LedgerBle", message.replace(PAYLOAD_VALUE, "value: <hidden>"))
        }
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        for (model in LedgerModel.bluetoothModels) {
            for (spec in model.bleServices) {
                val service = gatt.getService(spec.service) ?: continue
                val notify = service.getCharacteristic(spec.notify) ?: continue
                val writeWithoutResponse = service.getCharacteristic(spec.writeWithoutResponse)
                val write = writeWithoutResponse ?: service.getCharacteristic(spec.write) ?: continue
                notifyCharacteristic = notify
                writeCharacteristic = write
                writeType = if (writeWithoutResponse != null) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
                matchedModel = model
                return true
            }
        }
        return false
    }

    override fun initialize() {
        val notify = notifyCharacteristic ?: return
        requestMtu(REQUESTED_MTU)
            .with { _, mtu -> frameSize = maxOf(LedgerBleFraming.MIN_FRAME_SIZE, mtu - ATT_HEADER_LENGTH) }
            .enqueue()
        requestConnectionPriority(ConnectionPriorityRequest.CONNECTION_PRIORITY_HIGH).enqueue()
        setNotificationCallback(notify).with { _, data ->
            data.value?.let(::onPacket)
        }
        enableNotifications(notify)
            .done { notificationsReady.complete(Unit) }
            .fail { _, status ->
                if (status in AUTHENTICATION_STATUSES) {
                    isAuthenticationRejected = true
                }
                notificationsReady.completeExceptionally(LedgerFailureException(LedgerFailure.PairingRefused))
            }
            .enqueue()
    }

    override fun onServicesInvalidated() {
        notifyCharacteristic = null
        writeCharacteristic = null
    }

    suspend fun open(device: BluetoothDevice, connectTimeout: Duration, setupTimeout: Duration) {
        try {
            connect(device)
                .useAutoConnect(false)
                .retry(CONNECT_RETRIES, CONNECT_RETRY_DELAY_MS)
                .timeout(connectTimeout.inWholeMilliseconds)
                .suspend()
        } catch (e: RequestFailedException) {
            throw LedgerFailureException(
                when (e.status) {
                    FailCallback.REASON_DEVICE_NOT_SUPPORTED -> LedgerFailure.UnsupportedDevice
                    FailCallback.REASON_TIMEOUT -> LedgerFailure.Timeout(LedgerPhase.Connect)
                    FailCallback.REASON_BLUETOOTH_DISABLED -> LedgerFailure.BluetoothOff
                    else -> LedgerFailure.ConnectionLost
                },
            )
        } catch (_: InvalidRequestException) {
            throw LedgerFailureException(LedgerFailure.UnsupportedDevice)
        } catch (_: BluetoothDisabledException) {
            throw LedgerFailureException(LedgerFailure.BluetoothOff)
        } catch (_: DeviceDisconnectedException) {
            throw LedgerFailureException(LedgerFailure.ConnectionLost)
        } catch (_: SecurityException) {
            throw LedgerFailureException(LedgerFailure.NoPermission)
        }
        if (writeCharacteristic == null || notifyCharacteristic == null) {
            throw LedgerFailureException(LedgerFailure.UnsupportedDevice)
        }
        try {
            withTimeout(setupTimeout) { notificationsReady.await() }
        } catch (_: TimeoutCancellationException) {
            throw LedgerFailureException(LedgerFailure.Timeout(LedgerPhase.Setup))
        }
        negotiateFrameSize()
        if (!isLinkUp) {
            throw LedgerFailureException(LedgerFailure.ConnectionLost)
        }
    }

    private suspend fun negotiateFrameSize() {
        val write = writeCharacteristic ?: throw LedgerFailureException(LedgerFailure.ConnectionLost)
        try {
            writeCharacteristic(write, LedgerBleFraming.mtuHandshakeRequest, writeType).suspend()
        } catch (_: RequestFailedException) {
            throw LedgerFailureException(LedgerFailure.PairingRefused)
        } catch (_: DeviceDisconnectedException) {
            throw LedgerFailureException(LedgerFailure.ConnectionLost)
        } catch (_: BluetoothDisabledException) {
            throw LedgerFailureException(LedgerFailure.BluetoothOff)
        }
        val deviceFrameSize = withTimeoutOrNull(HANDSHAKE_TIMEOUT) { handshakeReply.await() }
        if (deviceFrameSize != null) {
            frameSize = deviceFrameSize
        }
    }

    suspend fun transmit(apdu: ByteArray, timeout: Duration?): ApduResponse = exchangeMutex.withLock {
        val write = writeCharacteristic
        if (!isLinkUp || write == null) {
            throw LedgerFailureException(LedgerFailure.ConnectionLost)
        }
        val pending = CompletableDeferred<ByteArray>()
        synchronized(reassembler) {
            reassembler.reset()
            inFlight = pending
        }
        try {
            val sizeForThisExchange = frameSize
            writeCharacteristic(write, apdu, writeType)
                .split(
                    DataSplitter { message, index, maxLength ->
                        LedgerBleFraming.frame(message, index, minOf(maxLength, sizeForThisExchange))
                    },
                )
                .suspend()
            val raw = if (timeout == null) { pending.await() } else { withTimeout(timeout) { pending.await() } }
            Apdu.parseResponse(raw)
        } catch (_: TimeoutCancellationException) {
            release()
            throw LedgerFailureException(LedgerFailure.Timeout(LedgerPhase.Apdu))
        } catch (_: DeviceDisconnectedException) {
            throw LedgerFailureException(LedgerFailure.ConnectionLost)
        } catch (_: BluetoothDisabledException) {
            throw LedgerFailureException(LedgerFailure.BluetoothOff)
        } catch (_: RequestFailedException) {
            throw LedgerFailureException(LedgerFailure.ConnectionLost)
        } catch (_: InvalidRequestException) {
            throw LedgerFailureException(LedgerFailure.ConnectionLost)
        } catch (e: IllegalArgumentException) {
            throw LedgerFailureException(LedgerFailure.Unexpected(e.message ?: "Malformed response"), cause = e)
        } finally {
            synchronized(reassembler) {
                if (inFlight === pending) {
                    inFlight = null
                }
            }
        }
    }

    suspend fun awaitLinkLost() {
        disconnected.await()
    }

    fun release() {
        if (closed) {
            return
        }
        closed = true
        onLinkLost()
        try {
            disconnect()
                .done { close() }
                .fail { _, _ -> close() }
                .enqueue()
        } catch (_: Throwable) {
            close()
        }
    }

    private fun onPacket(packet: ByteArray) {
        if (packet.isEmpty()) {
            return
        }
        if (packet[0] == LedgerBleFraming.TAG_MTU) {
            handshakeReply.complete(LedgerBleFraming.parseMtuHandshake(packet))
            return
        }
        synchronized(reassembler) {
            val pending = inFlight
            if (pending == null || pending.isCompleted) {
                return
            }
            try {
                reassembler.accept(packet)?.let(pending::complete)
            } catch (e: LedgerFramingException) {
                reassembler.reset()
                pending.completeExceptionally(LedgerFailureException(LedgerFailure.Unexpected(e.message ?: "Framing error"), cause = e))
            }
        }
    }

    private fun onLinkLost() {
        disconnected.complete(Unit)
        val lost = LedgerFailureException(LedgerFailure.ConnectionLost)
        notificationsReady.completeExceptionally(lost)
        handshakeReply.complete(null)
        inFlight?.completeExceptionally(lost)
    }

    private companion object {
        const val REQUESTED_MTU = 517
        const val ATT_HEADER_LENGTH = 3
        const val CONNECT_RETRIES = 2
        const val CONNECT_RETRY_DELAY_MS = 300
        val HANDSHAKE_TIMEOUT = 3.seconds
        val PAYLOAD_VALUE = Regex("value: .*")
        val AUTHENTICATION_STATUSES = setOf(
            BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION,
            BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION,
            GATT_INSUFFICIENT_AUTHORIZATION,
            GATT_AUTH_FAIL,
        )
        const val GATT_INSUFFICIENT_AUTHORIZATION = 8
        const val GATT_AUTH_FAIL = 137
    }
}

internal class LedgerBleLink(
    override val deviceId: String,
    private val connection: LedgerBleConnection,
) : LedgerLink {

    override val model: LedgerModel
        get() = connection.model

    override val isConnected: Boolean
        get() = connection.isLinkUp

    override suspend fun exchange(apdu: ByteArray, timeout: Duration?): ApduResponse = connection.transmit(apdu, timeout)

    override suspend fun awaitDisconnected() = connection.awaitLinkLost()

    override fun close() = connection.release()
}
