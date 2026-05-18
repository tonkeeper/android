package com.tonapps.blockchain.contract

sealed interface TokenType {

    val value: String
    val fmt: String

    enum class Defined(
        override val value: String,
        override val fmt: String,
    ) : TokenType {
        JETTON("jetton", "TON"),
        TRC20("trc-20", "TRC20"),
        ERC20("erc-20", "ERC20"),
        BEP20("bep-20", "BEP20"),
        SPL("spl", "SPL"),
    }

    data class Arbitrary(
        override val value: String,
        override val fmt: String = value.uppercase(),
    ) : TokenType

    companion object {
        fun from(name: String): TokenType {
            return try {
                Defined.entries
                    .first { it.value.equals(name, ignoreCase = true) }
            } catch (_: Throwable) {
                Arbitrary(name)
            }
        }
    }
}