package com.tonapps.ledgerkit.transport

import java.util.UUID

enum class LedgerModel(val productName: String, private val usbProductId: Int, bleServiceCodes: List<String>) {
    NanoS("Ledger Nano S", 0x10, emptyList()),
    NanoSPlus("Ledger Nano S Plus", 0x50, emptyList()),
    NanoX("Ledger Nano X", 0x40, listOf("0004")),
    Stax("Ledger Stax", 0x60, listOf("6004")),
    Flex("Ledger Flex", 0x70, listOf("3004")),
    NanoGen5("Ledger Nano Gen5", 0x80, listOf("8004", "9004"));

    val bleServices: List<LedgerBleService> = bleServiceCodes.map(LedgerBleService::fromModelCode)

    val supportsBluetooth: Boolean
        get() = bleServices.isNotEmpty()

    companion object {

        const val USB_VENDOR_ID = 0x2c97

        private const val LEGACY_PRODUCT_ID_SHIFT = 4
        private const val USB_INTERFACES_BITS = 8

        val bluetoothModels: List<LedgerModel> = entries.filter { it.supportsBluetooth }

        val bluetoothServices: List<LedgerBleService> = bluetoothModels.flatMap { it.bleServices }

        fun fromServiceUuid(uuid: UUID): LedgerModel? = bluetoothModels.firstOrNull { model ->
            model.bleServices.any { it.service == uuid }
        }

        fun fromName(name: String?): LedgerModel? = entries.firstOrNull { it.name == name }

        fun fromUsbProductId(productId: Int): LedgerModel? =
            entries.firstOrNull { it.usbProductId shr LEGACY_PRODUCT_ID_SHIFT == productId }
                ?: entries.firstOrNull { it.usbProductId == productId shr USB_INTERFACES_BITS }
    }
}

data class LedgerBleService(
    val service: UUID,
    val notify: UUID,
    val write: UUID,
    val writeWithoutResponse: UUID,
) {
    companion object {
        fun fromModelCode(code: String): LedgerBleService {
            fun uuid(characteristic: String) = UUID.fromString("13d63400-2c97-$code-$characteristic-4c6564676572")
            return LedgerBleService(
                service = uuid("0000"),
                notify = uuid("0001"),
                write = uuid("0002"),
                writeWithoutResponse = uuid("0003"),
            )
        }
    }
}
