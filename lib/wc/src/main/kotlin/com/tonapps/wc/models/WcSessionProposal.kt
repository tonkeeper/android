package com.tonapps.wc.models

data class WcSessionProposal(
    val topic: String,
    val app: WcAppData,
    val url: String,
    val chains: List<WcChain>,
)
