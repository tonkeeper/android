package com.tonapps.wc

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.DappConnectRequest
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wc.chains.WcRequestData
import com.tonapps.wc.models.WcConnection
import com.tonapps.wc.models.WcDappConnection
import com.tonapps.wc.models.WcSessionProposal
import com.tonapps.wc.models.WcValidation

interface WcRouteAdapter {
    fun <T : WcRequestData> convertRequest(
        requestId: String,
        walletId: String,
        chain: Chain,
        meta: WcDappConnection,
        rawRequest: T,
    ): ConfirmRequest?

    fun handleMultiSession(
        requestId: String,
        proposal: WcSessionProposal,
        wcConnection: WcConnection,
        validation: WcValidation,
    ): DappConnectRequest
}