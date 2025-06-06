package com.tonapps.tonkeeper.ui.screen.staking.viewer

import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.entities.StakedEntity
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.PoolDetailsEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StakeViewerViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val poolAddress: String,
    private val stakingRepository: StakingRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val api: API
): BaseWalletVM(app) {

    private val currency = settingsRepository.currency
    private val _poolFlow = MutableStateFlow<Pair<StakedEntity, PoolDetailsEntity>?>(null)
    private val poolFlow = _poolFlow.asStateFlow().filterNotNull()

    val poolNameFlow = poolFlow.map { it.first.pool.name }

    val uiItemsFlow = poolFlow.map { (staked, details) ->
        val liquidToken = staked.liquidToken
        val currencyCode = if (staked.isEthena) "USDe" else TokenEntity.TON.symbol
        val rates = ratesRepository.getRates(currency, listOfNotNull(
            currencyCode, liquidToken?.token?.address, WalletCurrency.USDE_TON_ETHENA_ADDRESS
        ))

        val amount = staked.balance
        val fiat = if (staked.isEthena) {
            liquidToken?.let {
                rates.convert(it.token.address, it.value)
            } ?: Coins.ZERO
        } else {
            rates.convert(TokenEntity.TON.symbol, amount)
        }

        val apyFormat = CurrencyFormatter.formatPercent(staked.pool.apy)

        val uiItems = mutableListOf<Item>()
        uiItems.add(Item.Balance(
            poolImplementation = staked.pool.implementation,
            balance = amount,
            balanceFormat = CurrencyFormatter.format(currencyCode, amount, customScale = 4),
            fiat = fiat,
            fiatFormat = CurrencyFormatter.formatFiat(currency.code, fiat),
            hiddenBalance = settingsRepository.hiddenBalances,
        ))
        uiItems.add(Item.Actions(poolAddress, wallet))

        if (liquidToken != null) {
            val tokenAddress = liquidToken.token.address
            val rateNow = rates.getRate(tokenAddress)
            val tokenFiat = rates.convert(tokenAddress, liquidToken.value)
            uiItems.add(Item.Space)
            uiItems.add(Item.Token(
                iconUri = liquidToken.token.imageUri,
                address = tokenAddress,
                symbol = liquidToken.token.symbol,
                name = liquidToken.token.name,
                balance = liquidToken.value,
                balanceFormat = CurrencyFormatter.format(liquidToken.token.symbol, liquidToken.value),
                fiat = tokenFiat,
                fiatFormat = CurrencyFormatter.formatFiat(currency.code, tokenFiat),
                rate = CurrencyFormatter.formatFiat(currency.code, rateNow),
                rateDiff24h = rates.getDiff7d(tokenAddress),
                verified = liquidToken.token.verification == TokenEntity.Verification.whitelist,
                testnet = wallet.testnet,
                hiddenBalance = settingsRepository.hiddenBalances,
                blacklist = liquidToken.token.verification == TokenEntity.Verification.blacklist,
                wallet = wallet,
            ))
            uiItems.add(Item.Space)
            if (staked.isEthena) {
                uiItems.add(Item.Description(Localization.stake_ethena_description))
            } else {
                uiItems.add(Item.Description(Localization.stake_tonstakers_description))
            }
            uiItems.add(Item.Space)
        }

        uiItems.add(Item.Space)
        uiItems.add(Item.Details(
            apyFormat = "≈ $apyFormat",
            minDepositFormat = if (staked.pool.minStake == Coins.ZERO) "" else CurrencyFormatter.format(currencyCode, staked.pool.minStake),
            maxApy = staked.maxApy
        ))
        uiItems.add(Item.Space)
        if (staked.isEthena) {
            uiItems.add(Item.Description(Localization.stake_ethena_disclaimer, "https://docs.ethena.fi/resources/terms-of-service".toUri()))
        } else {
            uiItems.add(Item.Description(Localization.staking_details_description))
        }
        uiItems.add(Item.Space)
        uiItems.add(Item.Links(details.getLinks(poolAddress)))
        uiItems
    }.flowOn(Dispatchers.IO)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val tokens = tokenRepository.get(currency, wallet.accountId, wallet.testnet) ?: return@launch
            val staking = stakingRepository.get(wallet.accountId, wallet.testnet)
            val staked = StakedEntity.create(wallet, staking, tokens, currency, ratesRepository, api)
            val item = staked.find { it.pool.address.equalsAddress(poolAddress) } ?: return@launch
            val details = staking.getDetails(item.pool.implementation) ?: return@launch
            _poolFlow.value = Pair(item, details)
        }
    }

}