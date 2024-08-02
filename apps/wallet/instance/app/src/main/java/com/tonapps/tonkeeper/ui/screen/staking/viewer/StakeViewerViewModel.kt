package com.tonapps.tonkeeper.ui.screen.staking.viewer

import androidx.lifecycle.ViewModel
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.entities.StakedEntity
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class StakeViewerViewModel(
    address: String,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
): ViewModel() {

    private val currency = settingsRepository.currency

    val uiItemsFlow = accountRepository.selectedWalletFlow.map { wallet ->
        val tonCode = TokenEntity.TON.symbol
        val rates = ratesRepository.getRates(currency, tonCode)
        val staking = stakingRepository.get(wallet.accountId, wallet.testnet)
        val pool = staking.findPoolByAddress(address) ?: throw IllegalArgumentException("Pool not found")
        val poolDetails = staking.getDetails(pool.implementation) ?: throw IllegalArgumentException("Pool details not found")
        val amount = staking.getAmount(pool)
        val fiat = rates.convert(tonCode, amount)

        val uiItems = mutableListOf<Item>()
        uiItems.add(Item.Balance(
            poolImplementation = pool.implementation,
            balance = amount,
            balanceFormat = CurrencyFormatter.format(tonCode, amount),
            fiat = fiat,
            fiatFormat = CurrencyFormatter.formatFiat(currency.code, fiat)
        ))
        uiItems.add(Item.Actions(address))
        uiItems.add(Item.Details(
            apyFormat = "≈ ${pool.apy}%",
            minDepositFormat = CurrencyFormatter.format(tonCode, pool.minStake)
        ))
        uiItems.add(Item.Links(poolDetails.getLinks(address)))
        uiItems
    }.flowOn(Dispatchers.IO)


}