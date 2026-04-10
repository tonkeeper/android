package com.tonapps.ledger.ble.service

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import com.tonapps.ledger.ble.extension.toHexString
import com.tonapps.ledger.ble.service.model.GattCallbackEvent
import com.tonapps.log.L
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking

class BleGattCallbackFlow : BluetoothGattCallback() {

    private val _gattFlow =
        MutableSharedFlow<GattCallbackEvent>(replay = 1, extraBufferCapacity = 0)
    val gattFlow: Flow<GattCallbackEvent>
        get() = _gattFlow

    private var hasDiscoveredService: Boolean = false

    private fun pushEvent(event: GattCallbackEvent) {
        runBlocking {
            _gattFlow.emit(event)
        }
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        L.d("GATT connection state change. state: $newState, status: $status")
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                pushEvent(GattCallbackEvent.ConnectionState.Connected)
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                pushEvent(GattCallbackEvent.ConnectionState.Disconnected)
            }
        }
    }

    override fun onServicesDiscovered(
        gatt: BluetoothGatt,
        status: Int
    ) {
        L.d("------------- onServicesDiscovered status: $status")
        if (status == BluetoothGatt.GATT_SUCCESS) {
            hasDiscoveredService = true
            pushEvent(
                GattCallbackEvent.ServicesDiscovered(gatt.services)
            )
        } else {
            L.w("onServicesDiscovered received: $status")
            pushEvent(GattCallbackEvent.ConnectionState.Disconnected)
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)
        //Seems that the callback can be reached without calling gatt.requestMtu(...)
        if (hasDiscoveredService) {
            L.d("------------ onMtuChanged => MTU new size: $mtu")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                pushEvent(GattCallbackEvent.MtuNegociated(mtu - GattInteractor.GATT_HEADER_SIZE))
            } else {
                L.w("onMtuChanged error with status : $status")
                pushEvent(GattCallbackEvent.ConnectionState.Disconnected)
            }
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        super.onDescriptorWrite(gatt, descriptor, status)
        L.d("------------- onDescriptorWrite status: $status")
        pushEvent(GattCallbackEvent.WriteDescriptorAck(status == BluetoothGatt.GATT_SUCCESS))
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        L.d("------------- onCharacteristicWrite status: $status")
        pushEvent(GattCallbackEvent.WriteCharacteristicAck(status == BluetoothGatt.GATT_SUCCESS))

    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        L.d("------------- onCharacteristicChanged status: ${characteristic.value.toHexString()}")
        pushEvent(GattCallbackEvent.CharacteristicChanged(characteristic.value))
    }

    fun clear() {
        hasDiscoveredService = false
        _gattFlow.resetReplayCache()
    }
}

