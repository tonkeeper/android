package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod

/**
 * What is available to pay a fee. Battery has no chain account — the relayer pays the chain fee — so
 * it can't be an [AccountWithDetails] and gets its own case.
 */
sealed interface FeeAccount {

    val balance: BigInteger?
    val rate: FiatRate?

    data class Chain(val value: AccountWithDetails) : FeeAccount {
        override val balance: BigInteger get() = value.unitBalance.value
        override val rate: FiatRate? get() = value.rate?.value
    }

    data class Keeper(
        override val balance: BigInteger?,
        override val rate: FiatRate?,
    ) : FeeAccount
}

/**
 * One way to pay a transaction's fee. What [fee] is denominated in says who pays the chain: its own
 * coin is paid by the wallet, [Asset.Battery] charges and a token of the transaction's own chain
 * (gasless, W5-only) are paid by the relayer, which broadcasts the transaction.
 */
data class TxFee(
    val account: FeeAccount,
    val fee: Fee,
    // False when the relayer or the amount being sent rules the method out, whatever the balance says.
    val available: Boolean = true,
) {
    val id: String
        get() = fee.asset.id

    val isNative: Boolean
        get() = fee.asset is Asset.Coin

    val isBattery: Boolean
        get() = fee.asset is Asset.Battery

    // A battery the relayer can pay this transaction with but has not priced, which is what a
    // wallet that never used the battery gets back. Listed so a recharge stays reachable, never paid.
    val isUnpriced: Boolean
        get() = isBattery && fee.amount <= BigInteger.ZERO

    // Legacy marks battery and gasless alike as `SendFee.RelayerFee` and sends them as internal messages.
    val viaRelayer: Boolean
        get() = !isNative
}

object TxFeeLogic {

    fun requiredEnergy(fee: Fee, attachedEnergy: BigInteger): BigInteger = fee.amount + attachedEnergy

    fun isSufficient(option: TxFee): Boolean {
        if (!option.available || option.isUnpriced) {
            return false
        }
        val balance = option.account.balance ?: return false
        return option.fee.amount <= balance
    }

    /**
     * A battery fee the charges fall short of: recharging pays it. Unknown charges are not "too
     * few", and a battery the relayer ruled out stays out whatever the charges. An unpriced one has
     * no fee to compare against: it is rechargeable only with no charges at all, where a recharge is
     * what gets it priced.
     */
    fun isRechargeable(option: TxFee): Boolean {
        if (!option.isBattery || !option.available) {
            return false
        }
        val balance = option.account.balance ?: return false
        if (option.isUnpriced) {
            return balance <= BigInteger.ZERO
        }
        return option.fee.amount > balance
    }

    fun resolve(options: List<TxFee>, pickedId: String?): TxFee? {
        if (options.isEmpty()) {
            return null
        }
        // An unavailable method cannot hold the selection: it goes back to the head of the list.
        val picked = options.firstOrNull { it.id == pickedId && it.available }
        if (picked != null && isSufficient(picked)) {
            return picked
        }
        val candidate = picked ?: options.first()
        if (isSufficient(candidate)) {
            return candidate
        }
        // The chain's own coin is the method that always exists, so it is the sanest fallback.
        return options.firstOrNull { it.isNative && isSufficient(it) }
            ?: options.firstOrNull { isSufficient(it) }
            ?: candidate
    }

    fun isPickerAvailable(options: List<TxFee>): Boolean {
        if (options.size > 1) {
            return true
        }
        return options.any { it.isBattery && !isSufficient(it) }
    }

    /**
     * The order methods are tried in: the kind the user chose last, then battery, the chain's coin, a
     * fee in the asset being sent, a fee in any other asset. Methods that cannot pay stay listed, in
     * the same order, below the ones that can.
     */
    fun prioritize(
        options: List<TxFee>,
        sentAssetId: String,
        preferred: PreferredFeeMethod,
    ): List<TxFee> {
        val ranked = options.sortedBy { rank(it, sentAssetId) }
        val (first, rest) = ranked.partition { isPreferred(it, preferred) }
        return payableFirst(first + rest)
    }

    fun payableFirst(options: List<TxFee>): List<TxFee> {
        val (payable, rest) = options.partition { isSufficient(it) }
        return payable + rest
    }

    /**
     * Prices the selected option until the selection holds: a quote can rule the option out, and the
     * one the selection moves to is an estimate again.
     */
    suspend fun quote(state: TxFeeState, pickedId: String?): TxFeeState {
        var current = state
        repeat(state.options.size) {
            val quote = current.quote ?: return current
            val selected = current.resolve(pickedId) ?: return current
            if (!selected.viaRelayer || !isSufficient(selected) || selected.id == current.quotedId) {
                return current
            }
            current = quote.quote(current, selected) ?: current.unavailable(selected)
        }
        return current
    }

    private fun rank(option: TxFee, sentAssetId: String): Int = when {
        option.isBattery -> 0
        option.isNative -> 1
        option.id.equals(sentAssetId, ignoreCase = true) -> 2
        else -> 3
    }

    private fun isPreferred(option: TxFee, preferred: PreferredFeeMethod): Boolean = when (preferred) {
        PreferredFeeMethod.BATTERY -> option.isBattery
        PreferredFeeMethod.TON -> option.isNative
        PreferredFeeMethod.GASLESS -> option.viaRelayer && !option.isBattery
        PreferredFeeMethod.UNSPECIFIED -> false
    }
}
