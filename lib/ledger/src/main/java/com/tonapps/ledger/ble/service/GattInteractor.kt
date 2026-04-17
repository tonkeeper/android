package com.tonapps.ledger.ble.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import com.tonapps.ledger.ble.extension.fromHexStringToBytes
import com.tonapps.ledger.ble.model.BleDeviceService
import kotlinx.coroutines.CoroutineScope
import com.tonapps.log.L

@SuppressLint("MissingPermission")
class GattInteractor(val gatt: BluetoothGatt) {

    init {
        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
    }

    fun discoverService(){
        L.d("Try discover services")
        gatt.discoverServices()
    }

    fun enableNotification(deviceService: BleDeviceService) {
        L.d("Enable Notification")
        gatt.setCharacteristicNotification(deviceService.notifyCharacteristic, true)
        deviceService.notifyCharacteristic.descriptors.forEach {
            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(it)
        }
    }

    fun negotiateMtu() {
        L.d("Megociate MTU")
        gatt.requestMtu(MAX_MTU_VALUE)
    }

    fun askMtu(deviceService: BleDeviceService) {
        L.d("Ask MTU size")
        deviceService.writeCharacteristic.value = BleService.MTU_HANDSHAKE_COMMAND.fromHexStringToBytes()
        gatt.writeCharacteristic(deviceService.writeCharacteristic)
    }

    fun sendBytes(deviceService: BleDeviceService, bytes: ByteArray) {
        deviceService.let {
            if (it.writeNoAnswerCharacteristic != null) {
                it.writeNoAnswerCharacteristic.value = bytes
                gatt.writeCharacteristic(it.writeNoAnswerCharacteristic)
            } else {
                it.writeCharacteristic.value = bytes
                gatt.writeCharacteristic(it.writeCharacteristic)
            }
        }
    }

    companion object{
        private const val MAX_MTU_VALUE = 512
        const val GATT_HEADER_SIZE = 3
    }
}