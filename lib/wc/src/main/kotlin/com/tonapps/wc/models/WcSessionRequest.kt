package com.tonapps.wc.models

import com.tonapps.chainkit.core.chain.model.account.Chain

data class WcSessionRequest(
    val method: WcMethod,
    val topic: String,
    val data: String,
    val chain: Chain,
    val dapp: WcDappConnection,
)
