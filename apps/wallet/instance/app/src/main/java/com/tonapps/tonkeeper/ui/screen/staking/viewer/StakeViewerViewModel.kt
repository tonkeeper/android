package com.tonapps.tonkeeper.ui.screen.staking.viewer

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.entities.StakedEntity
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class StakeViewerViewModel(
    app: Application,
    address: String,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository
): BaseWalletVM(app) {

    private val currency = settingsRepository.currency

    private val poolFlow = accountRepository.selectedWalletFlow.map { wallet ->
        val tokens = tokenRepository.get(currency, wallet.accountId, wallet.testnet) ?: throw IllegalArgumentException("Tokens not found")
        val staking = stakingRepository.get(wallet.accountId, wallet.testnet)
        val staked = StakedEntity.create(staking, tokens, currency, ratesRepository)
        val item = staked.find { it.pool.address.equalsAddress(address) } ?: throw IllegalArgumentException("Pool not found")
        val details = staking.getDetails(item.pool.implementation) ?: throw IllegalArgumentException("Details not found")
        Triple(item, details, wallet)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null).filterNotNull()

    val poolNameFlow = poolFlow.map { it.first.pool.name }

    val uiItemsFlow = poolFlow.map { (staked, details, wallet) ->
        val tonCode = TokenEntity.TON.symbol
        val rates = ratesRepository.getRates(currency, listOfNotNull(
            tonCode, staked.liquidToken?.token?.address
        ))

        val amount = staked.balance
        val fiat = rates.convert(tonCode, amount)
        val apyFormat = CurrencyFormatter.formatPercent(staked.pool.apy)

        val uiItems = mutableListOf<Item>()
        uiItems.add(Item.Balance(
            poolImplementation = staked.pool.implementation,
            balance = amount,
            balanceFormat = CurrencyFormatter.format(tonCode, amount, customScale = 4),
            fiat = fiat,
            fiatFormat = CurrencyFormatter.formatFiat(currency.code, fiat),
            hiddenBalance = settingsRepository.hiddenBalances,
        ))
        uiItems.add(Item.Actions(address))

        staked.liquidToken?.let { liquidToken ->
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
            uiItems.add(Item.Description(Localization.stake_tonstakers_description))
            uiItems.add(Item.Space)
        }

        uiItems.add(Item.Space)
        uiItems.add(Item.Details(
            apyFormat = "≈ $apyFormat",
            minDepositFormat = CurrencyFormatter.format(tonCode, staked.pool.minStake),
            maxApy = staked.maxApy
        ))
        uiItems.add(Item.Space)
        uiItems.add(Item.Description(Localization.staking_details_description))
        uiItems.add(Item.Space)
        uiItems.add(Item.Links(details.getLinks(address)))
        uiItems
    }.flowOn(Dispatchers.IO)


}