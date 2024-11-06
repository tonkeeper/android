package com.tonapps.wallet.data.rn

sealed class RNException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    data object EmptyChunks: RNException("wallets_chunks = 0") {
        private fun readResolve(): Any = EmptyChunks
    }

    data class NotFoundChunk(val chunk: Int): RNException("chunk $chunk not found")

    data class NotFoundMnemonic(val walletId: String): RNException("mnemonic for wallet $walletId not found")

    data object NotFoundPasscode: RNException("passcode not found") {
        private fun readResolve(): Any = NotFoundPasscode
    }

}