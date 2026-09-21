package com.tonapps.blockchain.model

import com.tonapps.chainkit.core.chain.model.account.Chain
import java.io.Serializable

data class DappConnectRequest(
    val id: String,
    val info: Info,
    val source: Source,
    val domainStatus: DomainStatus? = null,
    val chains: List<Chain>, // TODO filter required
) {

    class Info(
        val dappName: String,
        val dappHost: String,
        val iconUrl: String? = null,
        val topic: String? = null,
    ) : Serializable

    enum class Source { // TODO remove?
        Dapp, Browser, Qr;
        val isExternal: Boolean get() = this == Qr || this == Browser
        val isBrowser: Boolean get() = this == Browser
    }

    enum class DomainStatus {
        Valid, Invalid, Scam, Unknown;
    }
}


