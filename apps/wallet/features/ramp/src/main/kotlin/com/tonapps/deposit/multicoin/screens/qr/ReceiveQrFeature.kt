package com.tonapps.deposit.multicoin.screens.qr

import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.multicoin.analytics.DepositAnalytics
import com.tonapps.deposit.multicoin.domain.ReceiveAccountsInteractor
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.multichain.account.AccountEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ReceiveQrData(
    val assetId: String,
    val analyticsFrom: DepositFlowFrom,
)

class ReceiveQrFeature(
    private val data: ReceiveQrData,
    private val receiveAccounts: ReceiveAccountsInteractor,
) : AsyncViewModel() {

    sealed interface State {
        // The sheet is still animating in while this resolves — it shows nothing.
        data object Loading : State

        data class Qr(val account: AccountEntity) : State

        // No account for the requested asset (e.g. a legacy TON wallet asked for a non-TON
        // asset) — the screen falls back to the "Receiving address" list.
        data object AccountNotFound : State
    }

    val state: StateFlow<State> field = MutableStateFlow<State>(State.Loading)

    private val analytics = DepositAnalytics(RampType.RampOn, data.analyticsFrom)

    init {
        bgScope.launch {
            try {
                val account = receiveAccounts.findAccountForAsset(data.assetId)
                account?.let { entity ->
                    DepositAnalytics.networkOrNull(entity.network)?.let(analytics::viewReceiveTokens)
                }
                state.emit(account?.let { State.Qr(it) } ?: State.AccountNotFound)
            } catch (e: CancellationException) {
                // Not a `finally`: on ViewModel teardown AccountNotFound must not slip out and
                // trigger the fallback navigation.
                throw e
            } catch (_: Exception) {
                // A failed load must still resolve the screen — never leave it blank.
                state.emit(State.AccountNotFound)
            }
        }
    }
}
