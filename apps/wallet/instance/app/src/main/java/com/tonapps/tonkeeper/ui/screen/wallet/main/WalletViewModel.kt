package com.tonapps.tonkeeper.ui.screen.wallet.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.core.entities.TokenExtendedEntity
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.core.ScreenCacheSource
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.push.PushManager
import com.tonapps.wallet.data.push.entities.AppPushEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.collectFlow

class WalletViewModel(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val api: API,
    private val networkMonitor: NetworkMonitor,
    private val pushManager: PushManager,
    private val tonConnectRepository: TonConnectRepository,
    private val screenCacheSource: ScreenCacheSource,
    private val backupRepository: BackupRepository
): ViewModel() {

    private data class Tokens(
        val wallet: WalletEntity,
        val currency: WalletCurrency,
        val list: List<AccountTokenEntity>,
        val isOnline: Boolean,
        val push: List<AppPushEntity>,
        val apps: List<DAppEntity>,
    )

    private val _dataFlow = MutableStateFlow<Tokens?>(null)
    private val dataFlow = _dataFlow.asStateFlow().filterNotNull().filter { it.list.isNotEmpty() }

    private val _lastLtFlow = MutableStateFlow<Long>(0)
    private val lastLtFlow = _lastLtFlow.asStateFlow()

    private val _statusFlow = MutableSharedFlow<Item.Status>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val statusFlow = _statusFlow.asSharedFlow()

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filter { it.isNotEmpty() }

    val uiLabelFlow = accountRepository.selectedWalletFlow.map { it.label }

    init {
        collectFlow(accountRepository.selectedWalletFlow.map { screenCacheSource.getWalletScreen(it) }.flowOn(Dispatchers.IO)) { items ->
            if (items.isNullOrEmpty()) {
                _uiItemsFlow.value = listOf(Item.Skeleton(true))
            } else {
                _uiItemsFlow.value = items.map {
                    if (it !is Item.Token) {
                        it
                    } else if (it.isTON) {
                        it.copy(iconUri = TokenEntity.TON_ICON_URI)
                    } else if (it.isUSDT) {
                        it.copy(iconUri = TokenEntity.USDT_ICON_URI)
                    } else {
                        it
                    }
                }
            }
        }

        collectFlow(settingsRepository.hiddenBalancesFlow.drop(1)) { hiddenBalance ->
            val wallet = accountRepository.selectedWalletFlow.firstOrNull() ?: return@collectFlow
            val items = _uiItemsFlow.value.map {
                when (it) {
                    is Item.Balance -> it.copy(hiddenBalance = hiddenBalance)
                    is Item.Token -> it.copy(hiddenBalance = hiddenBalance)
                    else -> it
                }
            }
            _uiItemsFlow.value = items
            setCached(wallet, items)
        }

        /*collectFlow(walletRepository.realtimeEventsFlow) { event ->
            if (event is WalletEvent.Boc) {
                setStatus(Item.Status.SendingTransaction)
            } else if (event is WalletEvent.Transaction) {
                setStatus(Item.Status.TransactionConfirmed)
                delay(2000)
                setStatus(Item.Status.Default)
                _lastLtFlow.value = event.lt
            }
        }*/

        combine(
            accountRepository.selectedWalletFlow,
            settingsRepository.currencyFlow,
            networkMonitor.isOnlineFlow,
            lastLtFlow,
        ) { wallet, currency, isOnline, lastLt ->
            if (lastLt == 0L) {
                setStatus(Item.Status.Updating)
                _dataFlow.value = getLocalTokens(wallet, currency, isOnline, emptyList())
            }

            if (!isOnline) {
                return@combine null
            }

            getRemoteTokens(wallet, currency, emptyList())?.let { tokens ->
                setStatus(Item.Status.Default)
                _dataFlow.value = tokens
            }
        }.launchIn(viewModelScope)

        combine(
            accountRepository.selectedWalletFlow,
            backupRepository.stream,
            pushManager.dAppPushFlow,
        ) { wallet, backups, push ->
            val backupEntities = backups.filter { it.walletId == wallet.id }
            val pushEntities = push?.distinctBy { it.dappUrl } ?: emptyList()
        }.launchIn(viewModelScope)

        combine(
            accountRepository.selectedWalletFlow,
            dataFlow,
            statusFlow,
            settingsRepository.tokenPrefsChangedFlow,
        ) { wallet, tokens, status, _ ->
            val (fiatBalance, uiItems) = buildTokenUiItems(tokens.currency, tokens.wallet.testnet, tokens.list.map {
                TokenExtendedEntity(
                    raw = it,
                    prefs = settingsRepository.getTokenPrefs(wallet.id, it.address)
                )
            }.filter { !it.hidden }.sortedWith(TokenExtendedEntity.comparator))

            val balanceFormat = if (tokens.wallet.testnet) {
                CurrencyFormatter.formatFiat(TokenEntity.TON.symbol, fiatBalance)
            } else {
                CurrencyFormatter.formatFiat(tokens.currency.code, fiatBalance)
            }

            val actualStatus = if (tokens.isOnline) {
                status
            } else {
                Item.Status.NoInternet
            }
            setItems(tokens.wallet, balanceFormat, uiItems, actualStatus, tokens.push, tokens.apps)
        }.launchIn(viewModelScope)
    }

    fun nextWallet() {
        viewModelScope.launch {
            val wallets = accountRepository.getWallets()
            val activeWallet = accountRepository.selectedWalletFlow.firstOrNull() ?: return@launch
            val index = wallets.indexOf(activeWallet)
            val nextIndex = if (index == wallets.size - 1) 0 else index + 1
            accountRepository.setSelectedWallet(wallets[nextIndex].id)
        }
    }

    fun prevWallet() {
        viewModelScope.launch {
            val wallets = accountRepository.getWallets()
            val activeWallet = accountRepository.selectedWalletFlow.firstOrNull() ?: return@launch
            val index = wallets.indexOf(activeWallet)
            val prevIndex = if (index == 0) wallets.size - 1 else index - 1
            accountRepository.setSelectedWallet(wallets[prevIndex].id)
        }
    }

    private fun setStatus(status: Item.Status) {
        _statusFlow.tryEmit(status)
    }

    private suspend fun getLocalTokens(
        wallet: WalletEntity,
        currency: WalletCurrency,
        isOnline: Boolean,
        push: List<AppPushEntity>
    ): Tokens? = withContext(Dispatchers.IO) {
        val tokens = tokenRepository.getLocal(currency, wallet.accountId, wallet.testnet)
        if (tokens.isEmpty()) {
            return@withContext null
        }

        Tokens(wallet, currency, tokens, isOnline, push, getApps(wallet, push))
    }

    private suspend fun getRemoteTokens(
        wallet: WalletEntity,
        currency: WalletCurrency,
        push: List<AppPushEntity>
    ): Tokens? = withContext(Dispatchers.IO) {
        try {
            val tokens = tokenRepository.getRemote(currency, wallet.accountId, wallet.testnet)
            Tokens(wallet, currency, tokens, true, push, getApps(wallet, push))
        } catch (e: Throwable) {
            null
        }
    }

    private fun getApps(wallet: WalletEntity, events: List<AppPushEntity>): List<DAppEntity> {
        val dappUrls = events.map { it.dappUrl }
        return tonConnectRepository.getApps(dappUrls, wallet)
    }

    private fun buildTokenUiItems(
        currency: WalletCurrency,
        testnet: Boolean,
        tokens: List<TokenExtendedEntity>,
    ): Pair<Coins, List<Item.Token>> {
        var fiatBalance = Coins.of(0)
        if (testnet) {
            fiatBalance = tokens.first().balance.value
        }
        val uiItems = mutableListOf<Item.Token>()
        for ((index, token) in tokens.withIndex()) {
            fiatBalance += token.fiat

            val balanceFormat = CurrencyFormatter.format(value = token.balance.value)
            val fiatFormat = CurrencyFormatter.formatFiat(currency.code, token.fiat)

            val item = Item.Token(
                position = ListCell.getPosition(tokens.size, index),
                iconUri = token.imageUri,
                address = token.address,
                symbol = token.symbol,
                name = token.name,
                balance = token.balance.value,
                balanceFormat = balanceFormat,
                fiat = token.fiat,
                fiatFormat = fiatFormat,
                rate = CurrencyFormatter.formatFiat(currency.code, token.rateNow),
                rateDiff24h = token.rateDiff24h,
                verified = token.verified,
                testnet = testnet,
                hiddenBalance = settingsRepository.hiddenBalances
            )
            uiItems.add(item)
        }
        return Pair(fiatBalance, uiItems)
    }

    private fun setItems(
        wallet: WalletEntity,
        balance: CharSequence,
        list: List<Item.Token>,
        status: Item.Status,
        push: List<AppPushEntity>,
        apps: List<DAppEntity>,
    ) {
        val items = mutableListOf<Item>()
        items.add(
            Item.Balance(
            balance = balance,
            address = wallet.address,
            walletType = wallet.type,
            status = status,
            hiddenBalance = settingsRepository.hiddenBalances
        ))
        items.add(
            Item.Actions(
            address = wallet.address,
            token = TokenEntity.TON,
            walletType = wallet.type,
            swapUri = api.config.swapUri,
            disableSwap = api.config.flags.disableSwap
        ))
        if (push.isNotEmpty()) {
            items.add(Item.Push(push, apps))
        }
        items.add(Item.Space(true))
        items.addAll(list)
        items.add(Item.Space(true))
        if (list.size > 2) {
            items.add(Item.Manage(true))
        }
        _uiItemsFlow.value = items.toList()
        setCached(wallet, items)
    }

    private fun setCached(wallet: WalletEntity, items: List<Item>) {
        screenCacheSource.set(CACHE_NAME, wallet.id, items)
    }

    companion object {
        private const val CACHE_NAME = "wallet"

        fun ScreenCacheSource.getWalletScreen(wallet: WalletEntity): List<Item>? {
            try {
                val items: List<Item> = get(CACHE_NAME, wallet.id) { parcel ->
                    Item.createFromParcel(parcel)
                }.map {
                    if (it is Item.Balance) {
                        it.copy(status = Item.Status.Updating)
                    } else {
                        it
                    }
                }
                if (items.isEmpty()) {
                    return null
                }
                return items
            } catch (e: Throwable) {
                return null
            }
        }
    }
}