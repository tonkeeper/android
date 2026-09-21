package com.tonapps.deposit.multicoin.screens.confirm.engine

import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmationError
import com.tonapps.wallet.data.multichain.account.AccountWithDetails

/**
 * A relayer send prepared during estimation. Everything chain-specific the relayer needs — the quoted
 * TRON resources, the emulated forward amount — is captured here, so the confirm screen only ever knows
 * "there is a relayed way to send this".
 */
fun interface RelayerSend {

    suspend fun send(cryptoWallet: CryptoWallet, option: TxFee)
}

/**
 * The fee side of a prepared transaction: how it can be paid, what chainkit estimated, and how to
 * send it if a relayed method is chosen.
 */
data class TxFeeState(

    // Account the chain fee is charged to — the gas coin of the asset's chain.
    val energy: AccountWithDetails? = null,

    // Chainkit's estimate: signed with, and rendered when there is nothing to choose.
    val estimated: Fee? = null,

    // Empty when there is nothing to choose (messages, calls, swaps, plain native transfers); the
    // screen then renders [estimated] as a single read-only row.
    val options: List<TxFee> = emptyList(),

    val relayer: RelayerSend? = null,

    // Chainkit-path error from prepare time. Kept off the banner while a relayed method is selected —
    // a relayer pays the chain fee, so the wallet not covering it isn't an error — but a swipe on a
    // non-relayed method must still surface it.
    val error: ConfirmationError? = null,
) {

    fun findOption(id: String): TxFee? = options.firstOrNull { it.id == id }

    val hasBatteryOption: Boolean
        get() = options.any { it.account is FeeAccount.Keeper && TxFeeLogic.isSufficient(it) }

    fun resolve(pickedId: String?): TxFee? = TxFeeLogic.resolve(options, pickedId)

    val isPickerAvailable: Boolean
        get() = TxFeeLogic.isPickerAvailable(options)

    companion object {
        val Empty = TxFeeState()
    }
}
