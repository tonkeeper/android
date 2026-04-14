package com.tonapps.blockchain.model.account

import java.math.BigInteger

data class Balance(
    val available: Available,
    val pending: Pending? = null,
    val frozen: Frozen? = null,
    val locked: Locked? = null,
    val staked: Staked? = null,
    val rewards: Rewards? = null,
    val blocked: Blocked? = null,
    val claimable: Claimable? = null,
    val dust: Dust? = null,
) {

    val total: BigInteger by lazy {
        available.amount +
            (pending?.amount ?: BigInteger.ZERO) +
            (frozen?.amount ?: BigInteger.ZERO) +
            (locked?.amount ?: BigInteger.ZERO) +
            (staked?.amount ?: BigInteger.ZERO) +
            (rewards?.amount ?: BigInteger.ZERO) +
            (blocked?.amount ?: BigInteger.ZERO) +
            (claimable?.amount ?: BigInteger.ZERO) +
            (dust?.amount ?: BigInteger.ZERO)
    }

    sealed interface Type {
        val amount: BigInteger
    }

    data class Available(override val amount: BigInteger) : Type

    data class Pending(override val amount: BigInteger) : Type

    data class Frozen(override val amount: BigInteger) : Type

    data class Locked(override val amount: BigInteger) : Type

    data class Staked(override val amount: BigInteger) : Type

    data class Rewards(override val amount: BigInteger) : Type

    data class Blocked(override val amount: BigInteger) : Type

    data class Claimable(override val amount: BigInteger) : Type

    data class Dust(override val amount: BigInteger) : Type

    data class Transferable(override val amount: BigInteger) : Type

    data class Inscription(override val amount: BigInteger) : Type
}

