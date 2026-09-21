package com.tonapps.deposit.multicoin.screens.confirm.engine

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.num.Decimal
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.wallet.data.multichain.account.AccountWithDetails

/**
 * What a fee is denominated in, and what is available to pay it. Battery has no chain account — the
 * relayer pays the chain fee — so it can't be an [AccountWithDetails] and gets its own case.
 */
sealed interface FeeAccount {

    val decimals: Decimal
    val balance: BigInteger?
    val rate: FiatRate?

    data class Chain(val value: AccountWithDetails) : FeeAccount {
        // Same asset as `value.value.asset`, read without building the Account — that would
        // validate the address through the native lib, which Compose previews can't load.
        override val decimals: Decimal get() = value.asset.value.decimals
        override val balance: BigInteger get() = value.unitBalance.value
        override val rate: FiatRate? get() = value.rate?.value
    }

    data class Keeper(
        override val balance: BigInteger?,
        override val rate: FiatRate?,
    ) : FeeAccount {
        override val decimals: Decimal get() = Decimal(0)
    }
}

data class TxFee(
    val account: FeeAccount,
    val fee: Fee,

    // Whether the battery relayer broadcasts and pays the chain fee. Deliberately not derived from
    // [account]: a fee paid in a token of the transaction's own chain (gasless, W5-only) is relayed too,
    // exactly like battery — legacy marks both `SendFee.RelayerFee` and sends them as internal messages.
    val viaRelayer: Boolean,
) {
    val id: String
        get() = when (account) {
            is FeeAccount.Keeper -> "battery"
            is FeeAccount.Chain -> account.value.asset.id
        }

    val isNative: Boolean
        get() = account is FeeAccount.Chain && account.value.asset.value is Asset.Coin
}

object TxFeeLogic {

    fun isSufficient(option: TxFee): Boolean {
        val balance = option.account.balance ?: return false
        return option.fee.amount <= balance
    }

    fun resolve(options: List<TxFee>, pickedId: String?): TxFee? {
        if (options.isEmpty()) {
            return null
        }
        val picked = options.firstOrNull { it.id == pickedId }
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
        return options.any { it.account is FeeAccount.Keeper && !isSufficient(it) }
    }
}
