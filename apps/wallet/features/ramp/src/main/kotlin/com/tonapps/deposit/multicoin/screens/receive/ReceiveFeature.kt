package com.tonapps.deposit.multicoin.screens.receive

import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.multicoin.analytics.DepositAnalytics
import com.tonapps.deposit.multicoin.domain.ReceiveAccountsInteractor
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.multichain.account.AccountEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReceiveFeature(
    private val rampType: RampType,
    private val analyticsFrom: DepositFlowFrom,
    private val receiveAccounts: ReceiveAccountsInteractor,
) : AsyncViewModel() {

    val stateAccounts: StateFlow<List<AccountEntity>> field = MutableStateFlow(emptyList())

    // The account whose QR is open. The screen just observes this and calls selectAccount()
    // on tap — no local state needed.
    val selectedAccount: StateFlow<AccountEntity?> field = MutableStateFlow(null)

    private val analytics = DepositAnalytics(rampType, analyticsFrom)

    init {
        bgScope.launch {
            stateAccounts.emit(receiveAccounts.getAccounts())
        }
    }

    fun selectAccount(account: AccountEntity?) {
        selectedAccount.value = account
        account?.let { trackViewQr(it) }
    }

    private fun trackViewQr(account: AccountEntity) {
        DepositAnalytics.networkOrNull(account.network)?.let { analytics.viewReceiveTokens(it) }
    }
}
