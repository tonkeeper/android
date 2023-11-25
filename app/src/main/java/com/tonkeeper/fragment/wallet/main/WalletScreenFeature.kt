package com.tonkeeper.fragment.wallet.main

import android.net.Uri
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.api.account.AccountRepository
import com.tonkeeper.api.address
import com.tonkeeper.api.imageURL
import com.tonkeeper.api.jetton.JettonRepository
import com.tonkeeper.api.collectibles.CollectiblesRepository
import com.tonkeeper.api.collectionName
import com.tonkeeper.api.parsedBalance
import com.tonkeeper.api.title
import com.tonkeeper.api.userLikeAddress
import com.tonkeeper.core.Coin
import com.tonkeeper.core.currency.CurrencyManager
import com.tonkeeper.core.currency.from
import com.tonkeeper.event.ChangeCurrencyEvent
import com.tonkeeper.event.ChangeWalletNameEvent
import com.tonkeeper.event.UpdateCurrencyRateEvent
import com.tonkeeper.fragment.wallet.main.list.item.WalletItem
import com.tonkeeper.fragment.wallet.main.list.item.WalletJettonCellItem
import com.tonkeeper.fragment.wallet.main.list.item.WalletNftItem
import com.tonkeeper.fragment.wallet.main.list.item.WalletTonCellItem
import com.tonkeeper.fragment.wallet.main.pager.WalletScreenItem
import ton.SupportedCurrency
import ton.SupportedTokens
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
        queueScope.submit { updateWalletState() }
    }

    private val updateCurrencyRateAction = fun(_: UpdateCurrencyRateEvent) {
        queueScope.submit { updateWalletState() }
    }

    private val updateWalletNameAction = fun (event: ChangeWalletNameEvent) {
        updateUiState { currentState ->
            if (currentState.address == event.address) {
                currentState.copy(
                    title = event.name
                )
            } else {
                currentState
            }
        }
    }

    private val currency: SupportedCurrency
        get() = App.settings.currency

    private val currencyManager = CurrencyManager.getInstance()
    private val queueScope = QueueScope(Dispatchers.IO)
    private val accountRepository = AccountRepository()
    private val jettonRepository = JettonRepository()
    private val collectiblesRepository = CollectiblesRepository()

    init {
        requestWalletState()
        syncWallet()
        EventBus.subscribe(UpdateCurrencyRateEvent::class.java, updateCurrencyRateAction)
        EventBus.subscribe(ChangeCurrencyEvent::class.java, changeCurrencyAction)
        EventBus.subscribe(ChangeWalletNameEvent::class.java, updateWalletNameAction)
    }

    private fun syncWallet() {
        queueScope.submit {
            updateUiState { currentState ->
                currentState.copy(
                    asyncState = AsyncState.Loading
                )
            }

            val wallet = App.walletManager.getWalletInfo() ?: return@submit
            val address = wallet.address

            accountRepository.sync(address)
            jettonRepository.sync(address)
            collectiblesRepository.sync(address)

            updateWalletState()
        }
    }

    private fun requestWalletState() {
        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Loading
            )
        }

        queueScope.submit {
            updateWalletState()
        }
    }

    private suspend fun updateWalletState() {
        val wallet = App.walletManager.getWalletInfo() ?: return

        val data = getWalletData(wallet.address)

        val tonInCurrency = from(SupportedTokens.TON, data.accountId)
            .value(data.account.balance)
            .to(currency)

        val tokens = buildTokensList(
            data.accountId,
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
            val jettonInCurrency = from(jetton.address, data.accountId)
                .value(jetton.parsedBalance)
                .to(currency)

            allInCurrency += jettonInCurrency
        }

        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Default,
                title = wallet.name,
                currency = currency,
                address = data.accountId.userLikeAddress,
                tonBalance = data.account.balance,
                displayBalance = Coin.format(currency, allInCurrency),
                pages = pages
            )
        }
    }

    private suspend fun getRate(
        address: String,
        token: String
    ): Float {
        return currencyManager.getRate(
            address,
            token,
            currency.code
        )
    }

    private suspend fun getRate24h(
        address: String,
        token: String
    ): String {
        return currencyManager.getRate24h(
            address,
            token,
            currency.code
        )
    }

    private suspend fun buildTokensList(
        address: String,
        balance: Long,
        tonInCurrency: Float,
        jettons: List<JettonBalance>
    ): List<WalletItem> {
        val items = mutableListOf<WalletItem>()

        val rate = getRate(
            address,
            SupportedTokens.TON.code,
        )

        val rate24h = getRate24h(
            address,
            SupportedTokens.TON.code,
        )

        val tonItem = WalletTonCellItem(
            balance = Coin.format(value = balance),
            balanceCurrency = Coin.format(currency, tonInCurrency),
            rate = Coin.format(currency, rate),
            rateDiff24h = rate24h,
        )
        items.add(tonItem)

        for ((index, jetton) in jettons.withIndex()) {
            val cellPosition = ListCell.getPosition(jettons.size, index)
            val balance = jetton.parsedBalance
            val balanceCurrency = from(jetton.address, address)
                .value(balance)
                .to(currency)

            val rate = getRate(
                address,
                jetton.address,
            )

            val rate24h = getRate24h(
                address,
                jetton.address,
            )

            val item = WalletJettonCellItem(
                address = jetton.address,
                name = jetton.jetton.name,
                position = cellPosition,
                iconURI = Uri.parse(jetton.jetton.image),
                code = jetton.jetton.symbol,
                balance = Coin.format(value = balance),
                balanceCurrency = Coin.format(currency, balanceCurrency),
                rate = Coin.format(currency, rate),
                rateDiff24h = rate24h,
            )
            items.add(item)
        }

        return items
    }

    private fun buildNftList(collectibles: List<NftItem>): List<WalletItem> {
        val items = mutableListOf<WalletItem>()
        for (collectible in collectibles) {
            val item = WalletNftItem(
                nftAddress = collectible.address,
                imageURI = Uri.parse(collectible.imageURL),
                title = collectible.title,
                collectionName = collectible.collectionName,
                mark = false
            )
            items.add(item)
        }
        return items
    }

    override fun onCleared() {
        super.onCleared()
        queueScope.cancel()
        EventBus.unsubscribe(ChangeCurrencyEvent::class.java, changeCurrencyAction)
        EventBus.unsubscribe(UpdateCurrencyRateEvent::class.java, updateCurrencyRateAction)
        EventBus.unsubscribe(ChangeWalletNameEvent::class.java, updateWalletNameAction)
    }

    private suspend fun getWalletData(
        address: String
    ): WalletData = withContext(Dispatchers.IO) {
        val accountDeferred = async { accountRepository.get(address) }
        val jettonsDeferred = async { jettonRepository.get(address) }
        val collectiblesDeferred = async { collectiblesRepository.get(address) }

        val account = accountDeferred.await()
        val jettons = jettonsDeferred.await()
        val collectibles = collectiblesDeferred.await()

        return@withContext WalletData(
            accountId = address,
            account = account,
            jettons = jettons,
            collectibles = collectibles
        )
    }

    private data class WalletData(
        val accountId: String,
        val account: Account,
        val jettons: List<JettonBalance>,
        val collectibles: List<NftItem>
    )
}