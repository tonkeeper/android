package com.tonapps.blockchain.model.account


sealed interface Network {

    sealed interface Caip {
        val caip2: String
    }

    sealed interface Decimal {
        val number: Long
    }

    sealed interface Hex {
        val value: String
    }

    val group: String
    val id: String

    enum class Tvm(
        override val number: Long,
        override val group: String = "tvm",
        override val caip2: String = "ton"
    ) : Network, Decimal, Caip {
        Ton(number = -239),
        ;
        override val id: String get() = number.toString()
    }

    enum class Bitcoin(
        override val value: String,
        override val group: String = "bitcoin",
        override val caip2: String = "bip122",
    ) : Network, Hex, Caip {
        Bitcoin("000000000019d6689c085ae165831e93")
        ;
        override val id: String get() = value
    }

    enum class Tron(
        override val value: String,
        override val group: String = "tron",
        override val caip2: String = "tron",
    ): Network, Hex, Caip {
        Tron("0x2b6653dc")
        ;
        override val id: String get() = value
    }

    enum class Evm(
        override val number: Long,
        override val group: String = "evm",
        override val caip2: String = "eip155",
    ) : Network, Decimal, Caip {
        Ethereum(number = 1),
        Smartchain(number = 56),
        Arbitrum(number = 42161),
        Base(number = 8453),
        ;
        override val id: String get() = number.toString()
    }
}