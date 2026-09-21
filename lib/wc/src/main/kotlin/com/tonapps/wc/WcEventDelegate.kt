package com.tonapps.wc

import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import com.tonapps.log.L
import com.tonapps.wc.models.WcEventData
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

internal class WcEventDelegate : WalletKit.WalletDelegate {

    private val wcMutableEventFlow: MutableSharedFlow<WcEventData> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = Int.MAX_VALUE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<WcEventData> get() = wcMutableEventFlow

    override fun onConnectionStateChange(state: Wallet.Model.ConnectionState) {
        L.d("WalletConnect", "onConnectionStateChange ${state.isAvailable}")
        wcMutableEventFlow.tryEmit(WcEventData.ConnectionState(state))
    }

    override fun onError(error: Wallet.Model.Error) {
        L.d(error.throwable, "WalletConnect", "onError")
        wcMutableEventFlow.tryEmit(WcEventData.Error(error))
    }

    override fun onProposalExpired(proposal: Wallet.Model.ExpiredProposal) {
        L.d("WalletConnect", "onProposalExpired")
        // TODO implement extend session callback
    }

    override fun onRequestExpired(request: Wallet.Model.ExpiredRequest) {
        L.d("WalletConnect", "onRequestExpired")
        // TODO implement extend session callback
    }

    override fun onSessionDelete(sessionDelete: Wallet.Model.SessionDelete) {
        L.d("WalletConnect", "onSessionDelete")
        wcMutableEventFlow.tryEmit(WcEventData.SessionDelete(sessionDelete))
    }

    override fun onSessionExtend(session: Wallet.Model.Session) {
        L.d("WalletConnect", "onSessionExtend")
        // TODO implement extend session callback
    }

    override fun onSessionProposal(
        sessionProposal: Wallet.Model.SessionProposal,
        verifyContext: Wallet.Model.VerifyContext
    ) {
        L.d("WalletConnect", "onSessionProposal")
        wcMutableEventFlow.tryEmit(WcEventData.SessionProposal(sessionProposal, verifyContext))
    }

    override fun onSessionRequest(
        sessionRequest: Wallet.Model.SessionRequest,
        verifyContext: Wallet.Model.VerifyContext
    ) {
        L.d("WalletConnect", "onSessionRequest")
        wcMutableEventFlow.tryEmit(WcEventData.SessionRequest(sessionRequest))
    }

    override fun onSessionSettleResponse(settleSessionResponse: Wallet.Model.SettledSessionResponse) {
        L.d("WalletConnect", "onSessionSettleResponse")
        wcMutableEventFlow.tryEmit(WcEventData.SettledSessionResponse(settleSessionResponse))
    }

    override fun onSessionUpdateResponse(sessionUpdateResponse: Wallet.Model.SessionUpdateResponse) {
        L.d("WalletConnect", "onSessionUpdateResponse")
        wcMutableEventFlow.tryEmit(WcEventData.SessionUpdateResponse(sessionUpdateResponse))
    }
}
