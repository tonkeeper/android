package com.tonapps.tonkeeper.usecase.sign

import com.tonapps.wallet.data.account.Wallet

sealed class SignException(message: String): Exception(message) {

    data class UnsupportedWalletType(
        val type: Wallet.Type
    ): SignException("Unsupported wallet type: $type")



}