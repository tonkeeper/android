package com.tonapps.deposit.usecase.sign

import com.tonapps.blockchain.model.legacy.WalletType

sealed class SignException(message: String): Exception(message) {

    data class UnsupportedWalletType(
        val type: WalletType
    ): SignException("Unsupported wallet type: $type")
}