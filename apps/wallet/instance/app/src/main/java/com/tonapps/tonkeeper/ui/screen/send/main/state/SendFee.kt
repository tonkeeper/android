package com.tonapps.tonkeeper.ui.screen.send.main.state

import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.core.Fee
import com.tonapps.wallet.data.core.currency.WalletCurrency
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
        override val extra: Long
    ) : SendFee(), TokenFee, Extra

    data class Gasless(
        override val amount: Fee,
        override val fiatAmount: Coins,
        override val fiatCurrency: WalletCurrency,
        override val excessesAddress: AddrStd
    ) : SendFee(), TokenFee, RelayerFee

    data class Battery(
        val charges: Int,
        val chargesBalance: Int,
        override val extra: Long,
        override val excessesAddress: AddrStd
    ) : SendFee(), RelayerFee, Extra

}