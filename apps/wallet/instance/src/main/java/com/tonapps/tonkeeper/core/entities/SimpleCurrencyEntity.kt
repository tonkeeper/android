package com.tonapps.tonkeeper.core.entities

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.token.entities.AccountTokenEntity

data class SimpleCurrencyEntity(
    val code: String,
    val address: String,
    val decimals: Int,
) {

    constructor(currency: WalletCurrency) : this(
        currency.code,
        currency.code,
        currency.decimals
    )

    constructor(token: TokenEntity) : this(
        token.symbol,
        token.address,
        token.decimals
    )

    constructor(token: BalanceEntity) : this(token.token)

    constructor(token: AccountTokenEntity) : this(token.balance)

    fun coins(value: Double) = Coins.of(value, decimals)
}