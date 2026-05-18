package com.tonapps.deposit.screens.currency

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository

sealed interface SelectCurrencyAction : MviAction {
    data object Init : SelectCurrencyAction
}

sealed interface SelectCurrencyState : MviState {
    data object Loading : SelectCurrencyState
    data object Empty : SelectCurrencyState
    data class Data(val currencies: List<WalletCurrency>) : SelectCurrencyState
}

class SelectCurrencyViewState(
    val global: MviProperty<SelectCurrencyState>
) : MviViewState

class SelectCurrencyFeature(
    private val accountRepository: AccountRepository,
    private val exchangeRepository: ExchangeRepository,
    private val settingsRepository: SettingsRepository,
) : MviFeature<SelectCurrencyAction, SelectCurrencyState, SelectCurrencyViewState>(
    initState = SelectCurrencyState.Loading,
    initAction = SelectCurrencyAction.Init,
) {

    override fun createViewState(): SelectCurrencyViewState {
        return buildViewState {
            SelectCurrencyViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: SelectCurrencyAction) {
        when (action) {
            SelectCurrencyAction.Init -> {
                val wallet = accountRepository.forceSelectedWallet()
                val currencies =
                    exchangeRepository.getCurrencies(wallet.network, settingsRepository.getLocale())

                if (currencies.isEmpty()) {
                    setState { SelectCurrencyState.Empty }
                } else {
                    setState { SelectCurrencyState.Data(currencies) }
                }
            }
        }
    }
}
