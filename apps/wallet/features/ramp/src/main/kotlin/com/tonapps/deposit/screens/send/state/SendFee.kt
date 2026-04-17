package com.tonapps.deposit.screens.send.state

import com.tonapps.blockchain.model.legacy.Fee
import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import io.batteryapi.models.EstimatedTronTx
import org.ton.block.AddrStd

sealed class SendFee {

    interface TokenFee {
        val amount: Fee
        val fiatAmount: Coins
        val fiatCurrency: WalletCurrency
    }

    interface Extra {
        val extra: Long
    }

    interface RelayerFee {
        val excessesAddress: AddrStd
    }

    data class Ton(
        override val amount: Fee,
        override val fiatAmount: Coins,
        override val fiatCurrency: WalletCurrency,
        val error: Throwable? = null
    ) : SendFee(), TokenFee

    data class Gasless(
        override val amount: Fee,
        override val fiatAmount: Coins,
        override val fiatCurrency: WalletCurrency,
        override val excessesAddress: AddrStd,
    ) : SendFee(), TokenFee, RelayerFee

    data class Battery(
        val charges: Int,
        val chargesBalance: Int,
        override val extra: Long,
        override val excessesAddress: AddrStd,
        val fiatAmount: Coins,
        val fiatCurrency: WalletCurrency,
        val estimatedTron: EstimatedTronTx? = null,
        val excessCharges: Long? = null,
    ) : SendFee(), RelayerFee, Extra {
        val enoughCharges : Boolean
            get() = chargesBalance >= charges
    }

    data class TronTrx(
        override val amount: Fee,
        override val fiatAmount: Coins,
        override val fiatCurrency: WalletCurrency,
        val balance: Coins,
    ) : SendFee(), TokenFee {
        val enoughBalance : Boolean
            get() = balance >= amount.value
    }

    data class TronTon(
        override val amount: Fee,
        override val fiatAmount: Coins,
        override val fiatCurrency: WalletCurrency,
        val sendToAddress: String,
        val balance: Coins,
    ) : SendFee(), TokenFee {
        val enoughBalance : Boolean
            get() = balance >= amount.value + TransferEntity.POINT_ONE_TON
    }

}