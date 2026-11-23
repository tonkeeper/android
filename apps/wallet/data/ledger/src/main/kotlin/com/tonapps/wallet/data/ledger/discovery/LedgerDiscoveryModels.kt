package com.tonapps.wallet.data.ledger.discovery

import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.wallet.data.ledger.LedgerChainApp
import com.tonapps.wallet.data.ledger.LedgerDerivation
import com.tonapps.wallet.data.multichain.hardware.HardwareAccountDraft
import com.tonapps.wallet.data.multichain.hardware.HardwareWalletDescriptor

data class LedgerDevice(
    val id: String,
    val name: String,
    val model: LedgerModel?,
)

sealed interface LedgerAppDiscoveryState {
    data object Pending : LedgerAppDiscoveryState
    data object InProgress : LedgerAppDiscoveryState
    data object Done : LedgerAppDiscoveryState
    data class Skipped(val reason: LedgerFailure) : LedgerAppDiscoveryState
}

data class LedgerDiscoveredAccount(
    val index: Int,
    val accounts: List<HardwareAccountDraft>,
    val bitcoinMasterFingerprint: String?,
    val bitcoinAccountXpub: String?,
) {

    fun descriptor(device: LedgerDevice): HardwareWalletDescriptor = HardwareWalletDescriptor(
        deviceId = device.id,
        deviceName = device.name,
        deviceModel = device.model?.name,
        accountIndex = index,
        derivationPaths = accounts.associate { draft ->
            draft.chain.network.type.id to LedgerDerivation.defaultPath(draft.chain, index).toString()
        },
        bitcoinMasterFingerprint = bitcoinMasterFingerprint,
        bitcoinAccountXpub = bitcoinAccountXpub,
    )
}

data class LedgerDiscoveryResult(
    val accounts: List<LedgerDiscoveredAccount>,
    val apps: Map<LedgerChainApp, LedgerAppDiscoveryState>,
) {

    val skipped: Map<LedgerChainApp, LedgerFailure>
        get() = apps.mapNotNull { (app, state) -> (state as? LedgerAppDiscoveryState.Skipped)?.let { app to it.reason } }.toMap()
}
