package com.tonapps.deposit.multicoin.screens.receive

import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowDepositScreen
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowWalletMode
import com.tonapps.deposit.multicoin.analytics.DepositAnalytics
import com.tonapps.deposit.multicoin.domain.ReceiveAccountsInteractor
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.ledger.signing.LedgerSigningInteractor
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReceiveFeature(
    private val walletId: String,
    private val rampType: RampType,
    private val analyticsFrom: DepositFlowFrom,
    private val utm: UtmParameters,
    private val screen: DepositFlowDepositScreen,
    private val receiveAccounts: ReceiveAccountsInteractor,
    private val accountRepository: McAccountRepository,
    private val ledgerSigner: LedgerSigningInteractor,
) : AsyncViewModel() {

    val stateAccounts: StateFlow<List<ReceiveAccountItem>> field = MutableStateFlow(emptyList())

    // The account whose QR is open. The screen just observes this and calls selectAccount()
    // on tap — no local state needed.
    val selectedAccount: StateFlow<AccountEntity?> field = MutableStateFlow(null)

    val canVerifyOnLedger: StateFlow<Boolean> field = MutableStateFlow(false)

    private val analytics = DepositAnalytics(rampType, analyticsFrom, DepositFlowWalletMode.Multi, utm, screen)

    init {
        bgScope.launch {
            val accounts = receiveAccounts.getAccounts(walletId)
            stateAccounts.emit(accounts.toReceiveItems())
            canVerifyOnLedger.emit(accounts.firstOrNull()?.let { isHardware(it.walletId) } == true)
        }
    }

    fun verifyOnLedger(account: AccountEntity) {
        selectedAccount.value = null
        bgScope.launch {
            try {
                ledgerSigner.verifyAddress(account.walletId, account.chain)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LedgerFailureException) {
                L.e(e)
            }
        }
    }

    private suspend fun isHardware(walletId: String): Boolean = try {
        accountRepository.getWallet(walletId)?.isHardware == true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    fun selectAccount(account: AccountEntity?) {
        selectedAccount.value = account
        account?.let { trackViewQr(it) }
    }

    private fun trackViewQr(account: AccountEntity) {
        DepositAnalytics.networkOrNull(account.network)?.let { analytics.viewReceiveTokens(it) }
    }
}
