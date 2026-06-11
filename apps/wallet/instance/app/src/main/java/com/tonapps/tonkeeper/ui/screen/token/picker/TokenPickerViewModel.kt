package com.tonapps.tonkeeper.ui.screen.token.picker

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.legacy.enteties.AssetsEntity
import com.tonapps.legacy.enteties.AssetsExtendedEntity
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.token.picker.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class TokenPickerViewModel(
    app: Application,
    private val wallet: WalletEntity,
    selectedToken: TokenEntity,
    allowedTokens: List<String>,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val api: API,
): BaseWalletVM(app) {

    private val safeMode: Boolean = settingsRepository.isSafeModeEnabled(wallet.network)

    private val _selectedTokenFlow = MutableStateFlow(selectedToken)

    private val selectedTokenFlow = _selectedTokenFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
        .filterNotNull()

    private val _queryFlow = MutableStateFlow("")
    private val queryFlow = _queryFlow.asSharedFlow()

    private val tokensFlow = settingsRepository.currencyFlow.map { currency ->
        val tokens = tokenRepository.get(currency, wallet.accountId, wallet.network)?.filter {
            it.balance.isTransferable && !it.isTrx
        } ?: emptyList()

        val list = if (allowedTokens.isNotEmpty()) {
            tokens.filter { allowedTokens.contains(it.address) }
        } else {
            tokens
        }

        if (safeMode) {
            val safeModeList = mutableListOf< AccountTokenEntity>()
            for (token in list) {
                if (token.verified) {
                    safeModeList.add(token)
                }
            }
            safeModeList
        } else {
            list
        }
    }.flowOn(Dispatchers.IO)

    private val searchTokensFlow = combine(tokensFlow, queryFlow) { tokens, query ->
        tokens.filter { it.symbol.contains(query, ignoreCase = true) }
    }

    val uiItems = combine(
        selectedTokenFlow,
        searchTokensFlow
    ) { selectedToken, tokens ->
        val sortedTokens = tokens.map {
            AssetsExtendedEntity(
                raw = AssetsEntity.Token(it),
                prefs = settingsRepository.getTokenPrefs(wallet.id, it.address, it.blacklist),
                accountId = wallet.accountId,
            )
        }.filter { !it.hidden }.sortedWith(AssetsExtendedEntity.comparator)

        val tronUsdtEnabled = settingsRepository.getTronUsdtEnabled(wallet.id)

        sortedTokens.mapIndexed { index, tokenExtendedEntity ->
            val token = (tokenExtendedEntity.raw as AssetsEntity.Token).token
            Item.Token(
                position = ListCell.getPosition(sortedTokens.size, index),
                raw = token,
                selected = token.address == selectedToken.address,
                balance = CurrencyFormatter.format(token.symbol, token.balance.uiBalance),
                hiddenBalance = settingsRepository.hiddenBalances,
                showNetwork = tronUsdtEnabled && (token.isUsdt || token.isTrc20)
            )
        }
    }.flowOn(Dispatchers.IO)

    fun search(query: String) {
        _queryFlow.tryEmit(query)
    }
}