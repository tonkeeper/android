package com.tonapps.wallet.features.ledger.wallet

import androidx.compose.runtime.Immutable
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure

@Immutable
internal data class LedgerNetworkRow(
    val id: String,
    val chain: Chain,
    val displayAddress: String,
)

@Immutable
internal data class LedgerDeviceInfo(
    val deviceName: String,
    val modelName: String?,
    val accountNumber: Int,
)

@Immutable
internal data class LedgerWalletState(
    val device: LedgerDeviceInfo? = null,
    val networks: List<LedgerNetworkRow> = emptyList(),
    val missingNetworks: String? = null,
    val isLoaded: Boolean = false,
    val isLoadFailed: Boolean = false,
    val busyChain: Chain? = null,
    val isAddingNetworks: Boolean = false,
)

internal sealed interface LedgerWalletEvent {
    data object AddressVerified : LedgerWalletEvent
    data class NetworksAdded(val chains: List<Chain>, val skipReason: LedgerFailure?) : LedgerWalletEvent
    data class Failed(val failure: LedgerFailure?) : LedgerWalletEvent
    data class ConnectionChecked(val app: LedgerAppId?) : LedgerWalletEvent
    data object Close : LedgerWalletEvent
}

internal interface LedgerWalletActions {

    fun verify(chain: Chain) = Unit

    fun addNetworks() = Unit

    fun retryLoad() = Unit

    fun checkConnection() = Unit

    fun cancelCheck() = Unit
}
