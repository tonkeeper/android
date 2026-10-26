package com.tonapps.tonkeeper.ui.screen.ledger

import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentLedgerModel
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowLedgerModel
import com.tonapps.ledger.devices.DeviceModelId

fun DeviceModelId.toWalletFlowLedgerModel(): WalletFlowLedgerModel = when (this) {
    DeviceModelId.NANO_S -> WalletFlowLedgerModel.NanoS
    DeviceModelId.NANO_SP -> WalletFlowLedgerModel.NanoSPlus
    DeviceModelId.NANO_X -> WalletFlowLedgerModel.NanoX
    DeviceModelId.STAX -> WalletFlowLedgerModel.Stax
    DeviceModelId.EUROPA -> WalletFlowLedgerModel.Flex
    DeviceModelId.APEX -> WalletFlowLedgerModel.NanoGen5
    DeviceModelId.BLUE, DeviceModelId.USB -> WalletFlowLedgerModel.Unknown
}

fun DeviceModelId.toTransactionSentLedgerModel(): TransactionSentLedgerModel = when (this) {
    DeviceModelId.NANO_S -> TransactionSentLedgerModel.NanoS
    DeviceModelId.NANO_SP -> TransactionSentLedgerModel.NanoSPlus
    DeviceModelId.NANO_X -> TransactionSentLedgerModel.NanoX
    DeviceModelId.STAX -> TransactionSentLedgerModel.Stax
    DeviceModelId.EUROPA -> TransactionSentLedgerModel.Flex
    DeviceModelId.APEX -> TransactionSentLedgerModel.NanoGen5
    DeviceModelId.BLUE, DeviceModelId.USB -> TransactionSentLedgerModel.Unknown
}
