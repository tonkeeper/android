package com.tonapps.deposit.multicoin.screens.receive

import com.tonapps.deposit.multicoin.domain.NewChains
import com.tonapps.wallet.data.multichain.account.AccountEntity

data class ReceiveAccountItem(
    val account: AccountEntity,
    val isNew: Boolean,
)

internal fun List<AccountEntity>.toReceiveItems(): List<ReceiveAccountItem> =
    map { ReceiveAccountItem(account = it, isNew = it.chain in NewChains) }
