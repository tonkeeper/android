package com.tonapps.ledger.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelUuid
import com.tonapps.async.Async
import com.tonapps.ledger.ble.callback.BleManagerConnectionCallback
import com.tonapps.ledger.ble.callback.BleManagerDisconnectionCallback
import com.tonapps.ledger.ble.callback.BleManagerSendCallback
import com.tonapps.ledger.ble.extension.fromHexStringToBytes
import com.tonapps.ledger.ble.model.BleDeviceModel
import com.tonapps.ledger.ble.model.BleError
import com.tonapps.ledger.ble.model.BleEvent
import com.tonapps.ledger.ble.model.BleState
import com.tonapps.ledger.ble.service.BleService
import com.tonapps.ledger.ble.service.model.BleServiceEvent
import com.tonapps.ledger.devices.Devices
import com.tonapps.log.L
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.util.Date
import java.util.UUID

@SuppressLint("MissingPermission")
class BleManager internal constructor(
    private val context: Context
) {

    private val scope = Async.ioScope() + Job()

    private var isScanning: Boolean = false
    private val _bleState = MutableSharedFlow<BleState>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_LATEST,
        extraBufferCapacity = 10
    )

    val bleState: Flow<BleState>
        get() = _bleState

    //TODO improve events flow
    private val _bleEvents = MutableSharedFlow<BleEvent>()
    val bleEvents: Flow<BleEvent>
        get() = _bleEvents

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        try {
            context.getSystemService(BluetoothManager::class.java)?.adapter
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private val bluetoothScanner: BluetoothLeScanner?
        get() = try {
            bluetoothAdapter?.bluetoothLeScanner
        } catch (e: Throwable) {
            L.e(e)
            null
        }

    private var scannedDevices: MutableList<BleDeviceModel> = mutableListOf()
    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            L.d("Batch result")
            var added = false
            results?.let {
                results.forEach { res ->
                    val device = parseScanResult(res)
                    if (device != null && scannedDevices.find { scannedDevice -> scannedDevice.id == device.id } == null) {
                        scannedDevices.add(device)
                        onScanDevicesCallback?.invoke(scannedDevices)
                        added = true
                    }
                }
            }

            if (added) {
                onScanDevicesCallback?.invoke(scannedDevices)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            L.d("Bluetooth scan failed $errorCode")
            //TODO HANDLE ERROR IN BLESCANCALLBACK
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            when (callbackType) {
                ScanSettings.CALLBACK_TYPE_ALL_MATCHES,
                ScanSettings.CALLBACK_TYPE_FIRST_MATCH -> {
                    L.d("Scan result => FIRST_MATCH")
                    val device = parseScanResult(result)
                    //New Device Detected
                    if (device != null && scannedDevices.find { it.id == device.id } == null) {
                        scannedDevices.add(device)
                        onScanDevicesCallback?.invoke(scannedDevices)
                    }
                    //Known device (refresh)
                    else if (device != null && scannedDevices.find { it.id == device.id } != null) {
                        scannedDevices[scannedDevices.indexOfFirst { it.id == device.id }] = device
                    }
                }
                //Not called
                ScanSettings.CALLBACK_TYPE_MATCH_LOST -> {
                    L.d("Scan result => Lost")
                    if (scannedDevices.removeIf { it.id == result.device.address }) {
                        onScanDevicesCallback?.invoke(scannedDevices)
                    }
                }
            }

            L.d("Scan Devices $scannedDevices")
        }
    }

    var pollingJob: Job? = null
    var onScanDevicesCallback: ((List<BleDeviceModel>) -> Unit)? = null

    private var connectionCallback: BleManagerConnectionCallback? = null

    @Volatile
    private var connectingJob: Job? = null

    //- Disconnect
    private var disconnectionCallback: BleManagerDisconnectionCallback? = null

    @Volatile
    private var disconnectingJob: Job? = null

    private var disconnectingDeferred: CompletableDeferred<Boolean>? = null

    private val pendingSendRequest = mutableListOf<BleManagerSendCallback>()

    // Bluetooth Service lifecycle.
    private var bluetoothService: BleService? = null
    private lateinit var connectedDevice: BleDeviceModel
    var isConnected: Boolean = false
        private set

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            L.d("Connected to BleService !")
            bluetoothService = (service as BleService.LocalBinder).service
            bluetoothService?.let { bleService ->
                if (!bleService.initialize()) {
                    L.e("Unable to initialize Bluetooth")
                    connectionCallback?.onConnectionError(BleError.INITIALIZING_FAILED)
                    bleService.disconnectService(BleError.INITIALIZING_FAILED)
                } else {
                    bleService.connect(connectedDevice.id)
                    scope.launch {
                        bleService.listenEvents().collect { event ->
                            when (event) {
                                is BleServiceEvent.BleDeviceConnected -> {
                                    connectedDevice =
                                        connectedDevice.copy(serviceId = event.serviceUuid)
                                    connectionCallback?.onConnectionSuccess(connectedDevice)
                                    _bleState.tryEmit(BleState.Connected(connectedDevice))
                                }
                                is BleServiceEvent.BleDeviceDisconnected -> {
                                    _bleState.tryEmit(BleState.Disconnected(event.error))
                                    disconnected(event.error)
                                }
                                is BleServiceEvent.SuccessSend -> {
                                    _bleEvents.tryEmit(BleEvent.SendingEvent.SendSuccess(event.sendId))
                                }
                                is BleServiceEvent.SendAnswer -> {
                                    pendingSendRequest.firstOrNull { it.id == event.sendId }
                                        ?.let { callback ->
                                            callback.onSuccess(event.answer)
                                        }
                                }
                                is BleServiceEvent.ErrorSend -> {
                                    _bleEvents.tryEmit(BleEvent.Error.SendError(event.error))
                                    pendingSendRequest.firstOrNull { it.id == event.sendId }
                                        ?.let { callback ->
                                            callback.onError(event.error)
                                        }
                                }
                                else -> L.d("Event not handle $event")
                            }
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            L.d("BleService disconnected unexpectedly")
        }
    }

    private var tmpError: BleError? = null

    private fun parseScanResult(result: ScanResult): BleDeviceModel? {
        val device = result.device
        val rssi = result.rssi
        val uuids = getServiceUUIDsList(result)
        val name = device.name

        return if (name != null && uuids.isNotEmpty()) {
            L.d("Scan result device => \n id: ${device.address} \n name: $name \n serviceId : ${uuids.first()}")
            BleDeviceModel(
                id = device.address,
                name = name,
                serviceId = uuids.first().toString(),
                rssi = rssi
            )
        } else {
            null
        }
    }

    private fun getServiceUUIDsList(scanResult: ScanResult): List<UUID> {
        val parcelUuids = scanResult.scanRecord!!.serviceUuids
        val serviceList: MutableList<UUID> = ArrayList()
        for (i in parcelUuids.indices) {
            val serviceUUID = parcelUuids[i].uuid
            if (!serviceList.contains(serviceUUID)) {
                serviceList.add(serviceUUID)
            }
        }
        return serviceList
    }

    /**
     * Use bleState for getting informations about running scan
     */
    fun startScanning(): Boolean {
        return internalStartScanning()
    }

    fun startScanning(
        onScanDevices: (List<BleDeviceModel>) -> Unit
    ): Boolean {
        L.d("Start Scanning")
        onScanDevicesCallback = onScanDevices
        return internalStartScanning()
    }

    /**
     * Start a new scanning session
     *
     * Stop current device connection if exists
     * Stop
     */
    private fun internalStartScanning(): Boolean {
        disconnect()
        stopScanning()

        val scanner = bluetoothScanner ?: return false

        val filters = Devices.getBluetoothDevices().mapNotNull { device ->
            device.bluetoothSpec?.let { spec ->
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(UUID.fromString(spec.serviceUuid)))
                    .build()
            }
        }

        scannedDevices = mutableListOf()

        val builder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)

        val scanSettings = builder.build()
        try {
            scanner.startScan(filters, scanSettings, scanCallback)
        } catch (e: Throwable) {
            L.e(e)
            return false
        }

        isScanning = true

        if (pollingJob == null) {
            pollingJob = scope.launch {
                while (true) {
                    //Check outdated match
                    val currentTimestamp: Long = Date().time
                    if (scannedDevices.removeAll { it.timestamp + SCAN_MATCH_TTL < currentTimestamp }) {
                        onScanDevicesCallback?.invoke(scannedDevices)
                    }

                    _bleState.tryEmit(BleState.Scanning(scannedDevices = scannedDevices))
                    delay(SCAN_THROTTLE_MS)
                }
            }
        }

        return true
    }

    fun stopScanning() {
        L.d("Stop Scanning")
        pollingJob?.cancel()
        pollingJob = null
        try {
            bluetoothScanner?.stopScan(scanCallback)
        } catch (e: Throwable) {
            L.e(e)
        }
        isScanning = false
    }

    //Ensure only one connection is tried at a time
    @Synchronized
    fun connect(
        address: String,
        onConnectSuccess: (BleDeviceModel) -> Unit,
        onConnectError: (BleError) -> Unit
    ) {
        val callback = object : BleManagerConnectionCallback {
            override fun onConnectionSuccess(device: BleDeviceModel) {
                isConnected = true
                onConnectSuccess(device)
            }

            override fun onConnectionError(error: BleError) {
                onConnectError(error)
            }
        }

        if (connectingJob == null
            || connectingJob?.isCancelled == true
            || connectingJob?.isCompleted == true
        ) {
            connectingJob = scope.launch {
                internalConnect(address, callback)
            }
        }
    }

    /**
     * Use Event Flow for connection callback
     */
    @Synchronized
    fun connect(address: String) {
        if (connectingJob == null
            || connectingJob?.isCancelled == true
            || connectingJob?.isCompleted == true
        ) {
            connectingJob = scope.launch {
                internalConnect(address)
            }
        }
    }

    private suspend fun internalConnect(
        address: String,
        callback: BleManagerConnectionCallback? = null
    ) {
        L.d("($this) - Try Connecting to device with address $address")
        stopScanning()
        internalDisconnect()

        connectionCallback = callback

        val bondedDevices = try {
            bluetoothAdapter?.bondedDevices
        } catch (e: Throwable) {
            L.e(e)
            null
        }

        val device = scannedDevices.firstOrNull { it.id == address }
            ?: bondedDevices?.firstOrNull {
                it.address == address
            }?.let {
                BleDeviceModel(
                    id = it.address,
                    name = it.name,
                    serviceId = it.uuids?.first()?.uuid.toString(),
                )
            }

        device?.let {
            connectedDevice = it
            val gattServiceIntent = Intent(context, BleService::class.java)
            context.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } ?: run {
            connectionCallback?.onConnectionError(BleError.DEVICE_NOT_FOUND)
            _bleEvents.tryEmit(BleEvent.Error.ConnectionError(BleError.DEVICE_NOT_FOUND))
        }
    }

    @Synchronized
    fun disconnect(
        onDisconnectSuccess: () -> Unit,
    ) {
        L.d("Called disconnect")
        disconnectionCallback = object : BleManagerDisconnectionCallback {
            override fun onDisconnectionSuccess() {
                onDisconnectSuccess()
            }
        }

        if (disconnectingJob == null
            || disconnectingJob?.isCancelled == true
            || disconnectingJob?.isCompleted == true
        ) {
            disconnectingJob = scope.launch {
                internalDisconnect()
            }
        }
    }

    @Synchronized
    fun disconnect() {
        L.d("Called disconnect")

        if (disconnectingJob == null
            || disconnectingJob?.isCancelled == true
            || disconnectingJob?.isCompleted == true
        ) {
            disconnectingJob = scope.launch {
                internalDisconnect()
            }
        }
    }

    private suspend fun internalDisconnect() {
        L.d("internal Disconnect")
        if ((disconnectingDeferred == null
                || disconnectingDeferred?.isCompleted == true
                || disconnectingDeferred?.isCancelled == true)
            && (bluetoothService != null && bluetoothService!!.isBound)
        ) {
            disconnectingDeferred = CompletableDeferred()
            context.unbindService(serviceConnection)
            disconnectingDeferred!!.await()
        }
    }

    fun send(
        apdu: ByteArray,
    ) {
        bluetoothService?.sendApdu(apdu) ?: run {
            throw IllegalStateException("Bluetooth service not connected, please use connect before")
        }
    }

    fun send(
        apduHex: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val id = bluetoothService?.sendApdu(apduHex.fromHexStringToBytes()) ?: run {
            throw IllegalStateException("Bluetooth service not connected, please use connect before")
        }

        pendingSendRequest.add(
            BleManagerSendCallback(
                id = id,
                onSuccess = onSuccess,
                onError = onError
            )
        )
    }

    private fun disconnected(error: BleError? = null) {
        L.d("BleService disconnected")
        if (bluetoothService?.isBound == true) {
            tmpError = error
            context.unbindService(serviceConnection)
        } else {
            //Only Call disconnection or error
            if (tmpError == null && error == null) {
                disconnectionCallback?.onDisconnectionSuccess()
            } else {
                val errorToSend = error ?: tmpError
                connectionCallback?.onConnectionError(errorToSend!!)
            }

            tmpError = null
            disconnectionCallback = null
            connectionCallback = null
            bluetoothService = null
            isConnected = false
            disconnectingDeferred?.complete(true)
        }
    }

    companion object {
        private const val SCAN_MATCH_TTL = 5000L
        private const val SCAN_THROTTLE_MS = 1000L
    }
}