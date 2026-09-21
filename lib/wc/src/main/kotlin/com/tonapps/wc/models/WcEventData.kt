package com.tonapps.wc.models

import com.reown.walletkit.client.Wallet

internal sealed interface WcEventData {

    data class ConnectionState(
        val data: Wallet.Model.ConnectionState,
    ) : WcEventData

    data class Error(
        val data: Wallet.Model.Error,
    ) : WcEventData

    data class SessionDelete(
        val data: Wallet.Model.SessionDelete,
    ) : WcEventData

    data class SessionProposal(
        val data: Wallet.Model.SessionProposal,
        val verifyContext: Wallet.Model.VerifyContext,
    ) : WcEventData

    data class SessionRequest(
        val data: Wallet.Model.SessionRequest,
    ) : WcEventData

    data class SettledSessionResponse(
        val data: Wallet.Model.SettledSessionResponse,
    ) : WcEventData

    data class SessionUpdateResponse(
        val data: Wallet.Model.SessionUpdateResponse,
    ) : WcEventData
}
