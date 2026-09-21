package com.tonapps.wc.models

import com.tonapps.chainkit.core.chain.model.account.Chain

data class WcChain(
    val value: Chain,
    val isRequired: Boolean,
)