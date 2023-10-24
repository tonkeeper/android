package com.tonkeeper.fragment.wallet.main

import android.net.Uri
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.api.account.AccountRepository
import com.tonkeeper.api.address
import com.tonkeeper.api.description
import com.tonkeeper.api.imageURL
import com.tonkeeper.api.jetton.JettonRepository
import com.tonkeeper.api.nft.NftRepository
import com.tonkeeper.api.parsedBalance
import com.tonkeeper.api.title
import com.tonkeeper.core.Coin
import com.tonkeeper.core.currency.CurrencyManager
import com.tonkeeper.core.currency.from
import com.tonkeeper.event.ChangeCurrencyEvent
import com.tonkeeper.fragment.wallet.main.list.item.WalletItem
import com.tonkeeper.fragment.wallet.main.list.item.WalletJettonCellItem
import com.tonkeeper.fragment.wallet.main.list.item.WalletNftItem
import com.tonkeeper.fragment.wallet.main.list.item.WalletTonCellItem
import com.tonkeeper.fragment.wallet.main.pager.WalletScreenItem
import ton.SupportedCurrency
import ton.SupportedTokens
import ton.WalletInfo
import core.EventBus
import core.QueueScope
import io.tonapi.models.Account
import io.tonapi.models.JettonBalance
import io.tonapi.models.NftItem
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import uikit.list.ListCell

class WalletScreenFeature: UiFeature<WalletScreenState, WalletScreenEffect>(WalletScreenState()) {

    private val changeCurrencyAction = fun(_: ChangeCurrencyEvent) {
        loadWallet()
    }

    private val currency: SupportedCurrency
        get() = App.settings.currency

    private val queueScope = QueueScope(Dispatchers.IO)
    private val accountRepository = AccountRepository()
    private val jettonRepository = JettonRepository()
    private val nftRepository = NftRepository()

    private var cachedWallet: WalletInfo? = null

    init {
        loadWallet()
        syncWallet()
        EventBus.subscribe(ChangeCurrencyEvent::class.java, changeCurrencyAction)
    }

    private fun syncWallet() {
        queueScope.submit {
            updateUiState { currentState ->
                currentState.copy(
                    asyncState = AsyncState.Loading
                )
            }

            val wallet = getWallet() ?: return@submit
            val address = wallet.address

            accountRepository.sync(address)
            jettonRepository.sync(address)
            nftRepository.sync(address)
        }
    }

    private fun loadWallet() {
        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Loading
            )
        }

        queueScope.submit {
            val wallet = getWallet() ?: return@submit
            val address = wallet.address

            val data = getWalletData(address)


            val tonInCurrency = from(SupportedTokens.TON, address)
                .value(data.account.balance)
                .to(currency)

            val tokens = buildTokensList(
                address,
                data.account.balance,
                tonInCurrency,
                data.jettons
            )

            val nfts = buildNftList(data.collectibles)

            val pages = mutableListOf<WalletScreenItem>()

            if ((tokens.size + nfts.size) > 10) {
                pages.add(WalletScreenItem(
                    titleRes = R.string.tokens,
                    items = tokens
                ))

                pages.add(WalletScreenItem(
                    titleRes = R.string.collectibles,
                    items = nfts
                ))
            } else {
                pages.add(WalletScreenItem(
                    titleRes = R.string.tokens,
                    items = tokens + nfts
                ))
            }

            var allInCurrency = tonInCurrency

            for (jetton in data.jettons) {
                val jettonInCurrency = from(jetton.address, address)
                    .value(jetton.parsedBalance)
                    .to(currency)

                allInCurrency += jettonInCurrency
            }


            updateUiState { currentState ->
                currentState.copy(
                    asyncState = AsyncState.Default,
                    currency = currency,
                    address = address,
                    tonBalance = data.account.balance,
                    displayBalance = Coin.format(currency, allInCurrency),
                    pages = pages
                )
            }
        }
    }

    private suspend fun buildTokensList(
        address: String,
        balance: Long,
        tonInCurrency: Float,
        jettons: List<JettonBalance>
    ): List<WalletItem> {
        val items = mutableListOf<WalletItem>()

        val rate = CurrencyManager.getInstance().getRate(
            address,
            SupportedTokens.TON,
            currency
        )

        val rate24h = CurrencyManager.getInstance().getRate24h(
            address,
            SupportedTokens.TON,
            currency
        )

        val tonItem = WalletTonCellItem(
            balance = Coin.format(value = balance),
            balanceUSD = Coin.format(currency, tonInCurrency),
            rate = Coin.format(currency, rate),
            rateDiff24h = rate24h,
        )
        items.add(tonItem)

        for ((index, jetton) in jettons.withIndex()) {
            val cellPosition = ListCell.getPosition(jettons.size, index)
            val item = WalletJettonCellItem(
                position = cellPosition,
                iconURI = Uri.parse(jetton.jetton.image),
                code = jetton.jetton.symbol,
                balance = Coin.format(value = jetton.parsedBalance)
            )
            items.add(item)
        }

        return items
    }

    private fun buildNftList(collectibles: List<NftItem>): List<WalletItem> {
        val items = mutableListOf<WalletItem>()
        for (collectible in collectibles) {

            val item = WalletNftItem(
                imageURI = Uri.parse(collectible.imageURL),
                title = collectible.title,
                description = collectible.description,
                mark = false
            )
            items.add(item)
        }
        return items
    }

    private suspend fun getWallet(): WalletInfo? = withContext(Dispatchers.IO) {
        if (cachedWallet == null) {
            cachedWallet = App.walletManager.getWalletInfo()
        }
        cachedWallet
    }

    override fun onCleared() {
        super.onCleared()
        EventBus.unsubscribe(ChangeCurrencyEvent::class.java, changeCurrencyAction)
    }

    private suspend fun getWalletData(
        address: String
    ): WalletData = withContext(Dispatchers.IO) {
        val accountDeferred = async { accountRepository.get(address) }
        val jettonsDeferred = async { jettonRepository.get(address) }
        val collectiblesDeferred = async { nftRepository.get(address) }

        val account = accountDeferred.await()
        val jettons = jettonsDeferred.await()
        val collectibles = collectiblesDeferred.await()

        return@withContext WalletData(
            account = account,
            jettons = jettons,
            collectibles = collectibles
        )
    }

    private data class WalletData(
        val account: Account,
        val jettons: List<JettonBalance>,
        val collectibles: List<NftItem>
    )
}