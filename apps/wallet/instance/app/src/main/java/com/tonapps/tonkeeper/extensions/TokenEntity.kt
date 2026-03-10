package com.tonapps.tonkeeper.extensions

import com.tonapps.wallet.api.entity.value.Blockchain
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.core.currency.WalletCurrency.Chain

val TokenEntity.asCurrency: WalletCurrency
    get() = WalletCurrency(
        code = symbol,
        title = name,
        chain = if (blockchain == Blockchain.TRON) Chain.TRON(address, decimals) else Chain.TON(address, decimals),
        iconUrl = imageUri.toString(),
    )