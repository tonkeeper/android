package com.tonapps.blockchain.contract

import com.tonapps.blockchain.model.account.Account

class Wallet(
    val id: String,
    val name: String,
    val accounts: List<Account>,
    val secretSource: WalletType,
    val abstractionType: AbstractionType,
)
