package com.tonapps.tonkeeper.ui.screen.token.picker

import androidx.lifecycle.ViewModel
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.entities.TokenExtendedEntity
import com.tonapps.tonkeeper.ui.screen.token.picker.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn

class TokenPickerViewModel(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
): ViewModel() {

    private val _selectedTokenFlow = MutableStateFlow<TokenEntity?>(null)
    private val selectedTokenFlow = _selectedTokenFlow.asStateFlow().filterNotNull()

    private val _queryFlow = MutableStateFlow("")
    private val queryFlow = _queryFlow.asSharedFlow()

    private val tokensFlow = combine(accountRepository.selectedWalletFlow, settingsRepository.currencyFlow) { wallet, currency ->
        tokenRepository.getLocal(currency, wallet.accountId, wallet.testnet)
    }

    private val searchTokensFlow = combine(tokensFlow, queryFlow) { tokens, query ->
        tokens.filter { it.symbol.contains(query, ignoreCase = true) }
    }

    val uiItems = combine(accountRepository.selectedWalletFlow, selectedTokenFlow, searchTokensFlow) { wallet, selectedToken, tokens ->
        val sortedTokens = tokens.map {
            TokenExtendedEntity(
                raw = it,
                prefs = settingsRepository.getTokenPrefs(wallet.id, it.address)
            )
        }.filter { !it.hidden }.sortedWith(TokenExtendedEntity.comparator)

        sortedTokens.mapIndexed { index, tokenExtendedEntity ->
            val token = tokenExtendedEntity.raw
            Item.Token(
                position = ListCell.getPosition(sortedTokens.size, index),
                raw = token,
                selected = token.address == selectedToken.address,
                balance = CurrencyFormatter.format(token.symbol, token.balance.value)
            )
        }
    }.flowOn(Dispatchers.IO)

    fun setSelectedToken(token: TokenEntity) {
        _selectedTokenFlow.value = token
    }

    fun search(query: String) {
        _queryFlow.tryEmit(query)
    }
}