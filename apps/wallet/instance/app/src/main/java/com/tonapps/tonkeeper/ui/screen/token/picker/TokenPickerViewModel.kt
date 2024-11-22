package com.tonapps.tonkeeper.ui.screen.token.picker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.entities.AssetsEntity
import com.tonapps.tonkeeper.core.entities.AssetsExtendedEntity
import com.tonapps.tonkeeper.extensions.isSafeModeEnabled
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.token.picker.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import uikit.extensions.context

class TokenPickerViewModel(
    app: Application,
    wallet: WalletEntity,
    selectedToken: TokenEntity,
    allowedTokens: List<String>,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val api: API,
): BaseWalletVM(app) {

    private val safeMode: Boolean = settingsRepository.isSafeModeEnabled(api)

    private val _selectedTokenFlow = MutableStateFlow(selectedToken)
    private val selectedTokenFlow = _selectedTokenFlow.asStateFlow().filterNotNull()

    private val _queryFlow = MutableStateFlow("")
    private val queryFlow = _queryFlow.asSharedFlow()

    private val tokensFlow = settingsRepository.currencyFlow.map { currency ->
        val tokens = tokenRepository.get(currency, wallet.accountId, wallet.testnet)?.filter {
            it.balance.isTransferable
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

        sortedTokens.mapIndexed { index, tokenExtendedEntity ->
            val token = (tokenExtendedEntity.raw as AssetsEntity.Token).token
            Item.Token(
                position = ListCell.getPosition(sortedTokens.size, index),
                raw = token,
                selected = token.address == selectedToken.address,
                balance = CurrencyFormatter.format(token.symbol, token.balance.value),
                hiddenBalance = settingsRepository.hiddenBalances
            )
        }
    }.flowOn(Dispatchers.IO)

    fun search(query: String) {
        _queryFlow.tryEmit(query)
    }
}