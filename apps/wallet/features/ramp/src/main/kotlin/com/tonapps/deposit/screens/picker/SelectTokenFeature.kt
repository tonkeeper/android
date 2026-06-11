package com.tonapps.deposit.screens.picker

import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity

sealed interface TokenPickerAction : MviAction {
    data object Init : TokenPickerAction
}

sealed interface TokenPickerState : MviState {
    data object Loading : TokenPickerState
    data class Data(val tokens: List<AccountTokenEntity>) : TokenPickerState
}

class TokenPickerViewState(
    val global: MviProperty<TokenPickerState>
) : MviViewState

class TokenPickerFeature(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
) : MviFeature<TokenPickerAction, TokenPickerState, TokenPickerViewState>(
    initState = TokenPickerState.Loading,
    initAction = TokenPickerAction.Init,
) {

    override fun createViewState(): TokenPickerViewState {
        return buildViewState {
            TokenPickerViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: TokenPickerAction) {
        when (action) {
            TokenPickerAction.Init -> {
                val wallet = accountRepository.forceSelectedWallet()
                val currency = settingsRepository.currency
                val tokens = tokenRepository.get(currency, wallet.accountId, wallet.network)
                    ?.filter { it.balance.isTransferable && !it.isTrx }
                    ?: emptyList()

                setState { TokenPickerState.Data(tokens) }
            }
        }
    }
}
