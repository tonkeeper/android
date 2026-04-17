package com.tonapps.blockchain.model.transaction

import java.math.BigInteger

sealed interface Fee {

    val amount: BigInteger

    data object None : Fee {
        override val amount = BigInteger.ZERO
    }

    data class Value(
        override val amount: BigInteger,
    ) : Fee

    data class Gas(
        val limit: BigInteger,
        val price: BigInteger,
        override val amount: BigInteger = limit * price,
    ) : Fee

    enum class Priority {
        Low,
        Standard,
        High,
    }
}
