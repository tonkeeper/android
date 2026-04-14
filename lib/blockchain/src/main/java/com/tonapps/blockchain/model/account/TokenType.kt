package com.tonapps.blockchain.model.account

enum class TokenType {

    Jetton,
    Trc20,
    Erc20,
    Erc721,
    Erc1155,
    Bep20,
    SPL,
    Brc20,
    ;

    companion object {
        fun from(name: String): TokenType? {
            return try {
                TokenType.valueOf(name.replace("-", "")) // TODO
            } catch (_: Throwable) {
                return null
            }
        }
    }
}