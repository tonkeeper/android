package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.data.multichain.account.AccountWithDetails

/**
 * A relayer send prepared during estimation. Everything chain-specific the relayer needs — the quoted
 * TRON resources, the emulated forward amount — is captured here, so the confirm screen only ever knows
 * "there is a relayed way to send this".
 */
fun interface RelayerSend {

    suspend fun send(signer: SigningWallet, option: TxFee)
}

/**
 * For relayers whose listed options are only estimates: prices [option] for real and returns the state
 * that sends exactly what was priced, or null when the option cannot be paid after all.
 */
fun interface RelayerQuote {

    suspend fun quote(state: TxFeeState, option: TxFee): TxFeeState?
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

    val attachedEnergy: BigInteger = BigInteger.ZERO,

    // Empty when there is nothing to choose (messages, calls, swaps, plain native transfers); the
    // screen then renders [estimated] as a single read-only row.
    val options: List<TxFee> = emptyList(),

    val relayer: RelayerSend? = null,

    val quote: RelayerQuote? = null,

    // The option [quote] has priced, and the only one [relayer] sends.
    val quotedId: String? = null,

    // Chainkit-path error from prepare time. Kept off the banner while a relayed method is selected —
    // a relayer pays the chain fee, so the wallet not covering it isn't an error — but a swipe on a
    // non-relayed method must still surface it.
    val error: ConfirmationError? = null,

    // The battery fee priced for this transaction where one its charges cannot cover is kept out of
    // [options] (TON jetton and TRC20 USDT transfers): recharging would still pay the fee.
    val shortBattery: TxFee? = null,
) {

    val requiredEnergy: BigInteger?
        get() = estimated?.let { TxFeeLogic.requiredEnergy(it, attachedEnergy) }

    val hasBatteryOption: Boolean
        get() = options.any { it.isBattery && TxFeeLogic.isSufficient(it) }

    // Whether recharging the battery would pay this transaction's fee: the relayer priced it and
    // the charges fall short.
    val batteryRechargeable: Boolean
        get() = (options + listOfNotNull(shortBattery)).any(TxFeeLogic::isRechargeable)

    val pickerOptions: List<TxFee>
        get() = options.filterNot { it.isBattery && !it.available }

    val isPickerAvailable: Boolean
        get() = TxFeeLogic.isPickerAvailable(pickerOptions)

    fun findOption(id: String): TxFee? = options.firstOrNull { it.id == id }

    fun resolve(pickedId: String?): TxFee? = TxFeeLogic.resolve(options, pickedId)

    fun unavailable(option: TxFee): TxFeeState = copy(
        options = TxFeeLogic.payableFirst(
            options.map { listed ->
                if (listed.id == option.id) {
                    listed.copy(available = false)
                } else {
                    listed
                }
            }
        ),
    )

    companion object {
        val Empty = TxFeeState()
    }
}
