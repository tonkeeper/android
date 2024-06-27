package com.tonapps.ledger.devices

object Devices {
    private val devices: Map<DeviceModelId, DeviceModel> = mapOf(
        DeviceModelId.BLUE to DeviceModel(
            id = DeviceModelId.BLUE,
            productName = "Ledger Blue",
            productIdMM = 0x00,
            legacyUsbProductId = 0x0000,
            usbOnly = true,
            memorySize = 480 * 1024,
            masks = listOf(0x31000000, 0x31010000)
        ),
        DeviceModelId.NANO_S to DeviceModel(
            id = DeviceModelId.NANO_S,
            productName = "Ledger Nano S",
            productIdMM = 0x10,
            legacyUsbProductId = 0x0001,
            usbOnly = true,
            memorySize = 320 * 1024,
            masks = listOf(0x31100000),
        ),
        DeviceModelId.NANO_X to DeviceModel(
            id = DeviceModelId.NANO_X,
            productName = "Ledger Nano X",
            productIdMM = 0x40,
            legacyUsbProductId = 0x0004,
            usbOnly = false,
            memorySize = 2 * 1024 * 1024,
            masks = listOf(0x33000000),
            bluetoothSpec = listOf(
                BluetoothSpec(
                    serviceUuid = "13d63400-2c97-0004-0000-4c6564676572",
                    notifyUuid = "13d63400-2c97-0004-0001-4c6564676572",
                    writeUuid = "13d63400-2c97-0004-0002-4c6564676572",
                    writeCmdUuid = "13d63400-2c97-0004-0003-4c6564676572"
                )
            )
        ),
        DeviceModelId.NANO_SP to DeviceModel(
            id = DeviceModelId.NANO_SP,
            productName = "Ledger Nano S Plus",
            productIdMM = 0x50,
            legacyUsbProductId = 0x0005,
            usbOnly = true,
            memorySize = 1533 * 1024,
            masks = listOf(0x33100000)
        ),
        DeviceModelId.STAX to DeviceModel(
            id = DeviceModelId.STAX,
            productName = "Ledger Stax",
            productIdMM = 0x60,
            legacyUsbProductId = 0x0006,
            usbOnly = false,
            memorySize = 1533 * 1024,
            masks = listOf(0x33200000),
            bluetoothSpec = listOf(
                BluetoothSpec(
                    serviceUuid = "13d63400-2c97-6004-0000-4c6564676572",
                    notifyUuid = "13d63400-2c97-6004-0001-4c6564676572",
                    writeUuid = "13d63400-2c97-6004-0002-4c6564676572",
                    writeCmdUuid = "13d63400-2c97-6004-0003-4c6564676572"
                )
            )
        ),
        DeviceModelId.EUROPA to DeviceModel(
            id = DeviceModelId.EUROPA,
            productName = "Ledger Europa",
            productIdMM = 0x70,
            legacyUsbProductId = 0x0007,
            usbOnly = false,
            memorySize = 1533 * 1024,
            masks = listOf(0x33300000),
            bluetoothSpec = listOf(
                BluetoothSpec(
                    serviceUuid = "13d63400-2c97-3004-0000-4c6564676572",
                    notifyUuid = "13d63400-2c97-3004-0001-4c6564676572",
                    writeUuid = "13d63400-2c97-3004-0002-4c6564676572",
                    writeCmdUuid = "13d63400-2c97-3004-0003-4c6564676572"
                )
            )
        )
    )

    fun getBluetoothDevices(): List<DeviceModel> {
        return devices.values.filter { it.bluetoothSpec != null }
    }

    fun getDevices(): Map<DeviceModelId, DeviceModel> {
        return devices
    }

    fun fromServiceUuid(serviceUuid: String): DeviceModel {
        val device = devices.values.find { deviceModel ->
            deviceModel.bluetoothSpec?.any { it.serviceUuid == serviceUuid } ?: false
        }

        return device ?: devices[DeviceModelId.NANO_X]!!
    }
}
