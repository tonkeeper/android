package com.tonapps.wc.models

import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.wc.chains.WcRequestData

data class WcSessionData(
    val topic: String,
    val app: WcAppData,
    val chains: Set<Network.Type> = emptySet(),
) : WcRequestData
