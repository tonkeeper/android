package com.tonapps.tonkeeper.ui.screen.token.viewer

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.Formatter
import com.tonapps.tonkeeper.api.getCurrencyCodeByCountry
import com.tonapps.tonkeeper.core.entities.WalletPurchaseMethodEntity
import com.tonapps.tonkeeper.core.history.ActionOptions
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.extensions.isSafeModeEnabled
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.Blockchain
import com.tonapps.wallet.api.entity.ChartEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.settings.ChartPeriod
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import io.tonapi.models.AccountEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.toImmutableList

// TODO Refactor this class
class TokenViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val tokenAddress: String,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val api: API,
    private val eventsRepository: EventsRepository,
    private val historyHelper: HistoryHelper,
    private val batteryRepository: BatteryRepository,
    private val purchaseRepository: PurchaseRepository,
) : BaseWalletVM(app) {

    val burnAddress: String by lazy {
        api.getBurnAddress()
    }

    val installId: String
        get() = settingsRepository.installId

    private val _tokenFlow = MutableStateFlow<AccountTokenEntity?>(null)
    val tokenFlow = _tokenFlow.asStateFlow().filterNotNull()

    private val _uiItemsFlow = MutableStateFlow<List<Item>?>(null)
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filterNotNull()

    private val _uiHistoryFlow =
        MutableStateFlow<List<HistoryItem>>(listOf(HistoryItem.Loader(0, 0)))
    val uiHistoryFlow = _uiHistoryFlow.asStateFlow().filterNotNull()

    private val _chartFlow = MutableStateFlow<List<ChartEntity>?>(null)
    private val chartFlow = _chartFlow.asStateFlow().filterNotNull()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            tronAddress = accountRepository.getTronAddress(wallet.id)

            val list =
                tokenRepository.get(settingsRepository.currency, wallet.accountId, wallet.testnet)
                    ?: return@launch
            val token = list.firstOrNull { it.address == tokenAddress } ?: return@launch
            _tokenFlow.value = token
            buildItems(token, emptyList())
            load(token)
        }

        combine(
            tokenFlow,
            chartFlow,
            settingsRepository.walletPrefsChangedFlow
        ) { token, chart, _ ->
            buildItems(token, chart)
        }.launchIn(viewModelScope)
    }

    fun setChartPeriod(period: ChartPeriod) {
        settingsRepository.chartPeriod = period
        _tokenFlow.filterNotNull().take(1).onEach {
            loadChartPeriod(it, period)
        }.launchIn(viewModelScope)
    }

    private suspend fun loadChartPeriod(token: AccountTokenEntity, period: ChartPeriod) {
        _chartFlow.value = emptyList()

        when (period) {
            ChartPeriod.hour -> loadHourChart(token)
            ChartPeriod.day -> loadDayChart(token)
            ChartPeriod.week -> loadWeekChart(token)
            ChartPeriod.month -> loadMonthChart(token)
            ChartPeriod.halfYear -> load6MonthChart(token)
            ChartPeriod.year -> loadYearChart(token)
        }
    }

    private suspend fun load(token: AccountTokenEntity) = withContext(Dispatchers.IO) {
        if (token.verified) {
            loadHistory(token)
            loadChartPeriod(token, settingsRepository.chartPeriod)
        } else {
            _chartFlow.value = emptyList()
            loadHistory(token)
        }
    }

    fun loadMore() {
        if (isLoading()) {
            return
        }

        viewModelScope.launch {
            val lastLt = lastLt() ?: return@launch
            val data = _tokenFlow.value ?: return@launch
            val oldValues = _uiHistoryFlow.value ?: emptyList()
            _uiHistoryFlow.value = historyHelper.withLoadingItem(oldValues)
            loadHistory(data, lastLt)
        }
    }

    private suspend fun hasW5(): Boolean {
        if (wallet.version == WalletVersion.V5R1) {
            return true
        } else if (wallet.type == Wallet.Type.Watch || wallet.type == Wallet.Type.Lockup || wallet.type == Wallet.Type.Ledger) {
            return true
        }
        val w5Contact = BaseWalletContract.create(wallet.publicKey, "v5r1", wallet.testnet)
        val accountId = w5Contact.address.toAccountId()
        return accountRepository.getWalletByAccountId(accountId, wallet.testnet) != null
    }

    private suspend fun buildItems(
        token: AccountTokenEntity,
        charts: List<ChartEntity>
    ) {
        val currency = settingsRepository.currency.code
        val items = mutableListOf<Item>()
        items.add(
            Item.Balance(
                balance = CurrencyFormatter.format(
                    token.symbol,
                    token.balance.value,
                    token.decimals
                ),
                fiat = CurrencyFormatter.format(currency, token.fiat),
                iconUri = token.imageUri,
                hiddenBalance = settingsRepository.hiddenBalances,
                showNetwork = tronUsdtEnabled && (token.isUsdt || token.isTrc20),
                blockchain = token.token.blockchain,
            )
        )
        items.add(
            Item.Actions(
                swapUri = api.config.swapUri,
                swapMethod = if (token.isTrc20) getSwapMethod() else null,
                token = token.balance.token,
                wallet = wallet,
            )
        )
        if (token.isUsdt && !wallet.isW5 && wallet.hasPrivateKey && settingsRepository.isUSDTW5(
                wallet.id
            )
        ) {
            items.add(
                Item.W5Banner(
                    wallet = wallet,
                    addButton = !hasW5()
                )
            )
        }

        if (wallet.hasPrivateKey && token.isTrc20) {
            val batteryCharges = getBatteryCharges()
            if (batteryCharges < 300) {
                items.add(
                    Item.BatteryBanner(
                        wallet = wallet,
                        token = token.balance.token
                    )
                )
            }
        }

        if (token.hasRate && !token.isUsdt) {
            val period = settingsRepository.chartPeriod
            val fiatPrice: CharSequence
            val rateDiff24h: String
            val delta: CharSequence
            if (2 >= charts.size) {
                fiatPrice = CurrencyFormatter.format(currency, token.rateNow, 4)
                rateDiff24h = token.rateDiff24h
                delta = ""
            } else {

                val maxPrice = charts.maxOf { it.price }
                val minPrice = charts.minOf { it.price }

                val firstFiatPrice = token.rateNow.toFloat()
                val lastFiatPrice = charts.first().price

                val priceDelta: Coins
                val growPercent: Float

                if (maxPrice == minPrice) {
                    priceDelta = Coins.ZERO
                    growPercent = 0f
                } else {
                    priceDelta = Coins.of(firstFiatPrice - lastFiatPrice, token.decimals)
                    growPercent = (firstFiatPrice - lastFiatPrice) / firstFiatPrice * 100
                }


                val priceDeltaFormat = CurrencyFormatter.formatFiat(currency, priceDelta)
                val growPercentFormat = Formatter.percent(growPercent)

                fiatPrice =
                    CurrencyFormatter.format(settingsRepository.currency.code, token.rateNow, 4)
                rateDiff24h = growPercentFormat
                delta = priceDeltaFormat
            }


            items.add(
                Item.Chart(
                    data = charts,
                    square = period == ChartPeriod.hour,
                    period = period,
                    fiatPrice = fiatPrice,
                    rateDiff24h = rateDiff24h,
                    delta = delta,
                    currency = settingsRepository.currency,
                    rateNow = token.rateNow
                )
            )
        }

        _uiItemsFlow.value = items
    }

    private suspend fun loadHistory(
        token: AccountTokenEntity,
        beforeLt: Long? = null
    ) = withContext(Dispatchers.IO) {
        if (token.token.blockchain === Blockchain.TRON) {
            loadTronHistory(beforeLt)
        } else {
            loadTonHistory(token, beforeLt)
        }
    }

    private suspend fun loadTonHistory(
        token: AccountTokenEntity,
        beforeLt: Long? = null
    ) = withContext(Dispatchers.IO) {
        val accountEvents =
            eventsRepository.loadForToken(token.address, wallet.accountId, wallet.testnet, beforeLt)
                ?: return@withContext
        val walletEventItems = mapping(wallet, accountEvents.events)
        if (beforeLt == null) {
            setEvents(walletEventItems)
        } else {
            val oldValue =
                (_uiHistoryFlow.value).toImmutableList().filter { it !is HistoryItem.Loader }
            setEvents(oldValue + walletEventItems)
        }
    }

    private suspend fun loadTronHistory(
        beforeLt: Long? = null
    ) = withContext(Dispatchers.IO) {
        if (tronAddress == null) {
            return@withContext
        }

        val tonProofToken =
            accountRepository.requestTonProofToken(wallet) ?: return@withContext
        val tronEvents = eventsRepository.loadTronEvents(tronAddress!!, tonProofToken, beforeLt) ?: return@withContext
        val walletEventItems = historyHelper.tronMapping(
            wallet = wallet,
            tronAddress = tronAddress!!,
            events = tronEvents,
            options = ActionOptions(
                safeMode = settingsRepository.isSafeModeEnabled(api),
                hiddenBalances = settingsRepository.hiddenBalances,
            )
        )

        if (beforeLt == null) {
            setEvents(walletEventItems)
        } else {
            val oldValue =
                (_uiHistoryFlow.value).toImmutableList().filter { it !is HistoryItem.Loader }
            setEvents((oldValue + walletEventItems).distinctBy { it.uniqueId })
        }
    }

    private suspend fun mapping(
        wallet: WalletEntity,
        events: List<AccountEvent>
    ): List<HistoryItem> {
        return historyHelper.mapping(
            wallet = wallet,
            events = events,
            options = ActionOptions(
                safeMode = settingsRepository.isSafeModeEnabled(api),
                hiddenBalances = settingsRepository.hiddenBalances,
                tronEnabled = tronUsdtEnabled,
            )
        )
    }

    private fun setEvents(
        items: List<HistoryItem>
    ) {
        _uiHistoryFlow.value = historyHelper.groupByDate(items)
    }

    private fun foundLastItem(): HistoryItem.Event? {
        return _uiHistoryFlow.value?.lastOrNull { it is HistoryItem.Event } as? HistoryItem.Event
    }

    private fun isLoading(): Boolean {
        return _uiHistoryFlow.value?.lastOrNull { it is HistoryItem.Loader } != null
    }

    private fun lastLt(): Long? {
        val item = foundLastItem() ?: return null
        if (item.lt > 0) {
            return item.lt
        }
        return null
    }

    private suspend fun loadChart(
        token: AccountTokenEntity,
        startDateSeconds: Long,
        endDateSeconds: Long
    ) = withContext(Dispatchers.IO) {
        if (token.verified && !token.isUsdt && !token.isTrc20) {
            _chartFlow.value = api.loadChart(
                token.address,
                settingsRepository.currency.code,
                startDateSeconds,
                endDateSeconds
            )
        } else {
            _chartFlow.value = emptyList()
        }
    }

    private suspend fun loadHourChart(token: AccountTokenEntity) {
        val endDateSeconds = System.currentTimeMillis() / 1000
        val startDateSeconds = endDateSeconds - 60 * 60
        loadChart(token, startDateSeconds, endDateSeconds)
    }

    private suspend fun loadDayChart(token: AccountTokenEntity) {
        val endDateSeconds = System.currentTimeMillis() / 1000
        val startDateSeconds = endDateSeconds - 60 * 60 * 24
        loadChart(token, startDateSeconds, endDateSeconds)
    }

    private suspend fun loadWeekChart(token: AccountTokenEntity) {
        val endDateSeconds = System.currentTimeMillis() / 1000
        val startDateSeconds = endDateSeconds - 60 * 60 * 24 * 7
        loadChart(token, startDateSeconds, endDateSeconds)
    }

    private suspend fun loadMonthChart(token: AccountTokenEntity) {
        val endDateSeconds = System.currentTimeMillis() / 1000
        val startDateSeconds = endDateSeconds - 60 * 60 * 24 * 30
        loadChart(token, startDateSeconds, endDateSeconds)
    }

    private suspend fun load6MonthChart(token: AccountTokenEntity) {
        val endDateSeconds = System.currentTimeMillis() / 1000
        val startDateSeconds = endDateSeconds - 60 * 60 * 24 * 30 * 6
        loadChart(token, startDateSeconds, endDateSeconds)
    }

    private suspend fun loadYearChart(token: AccountTokenEntity) {
        val endDateSeconds = System.currentTimeMillis() / 1000
        val startDateSeconds = endDateSeconds - 60 * 60 * 24 * 365
        loadChart(token, startDateSeconds, endDateSeconds)
    }

    private suspend fun getBatteryCharges(): Int = withContext(Dispatchers.IO) {
        accountRepository.requestTonProofToken(wallet)?.let {
            batteryRepository.getCharges(it, wallet.publicKey, wallet.testnet, true)
        } ?: 0
    }

    private suspend fun getSwapMethod() = withContext(Dispatchers.IO) {
        if (!tronUsdtEnabled) {
            return@withContext null
        }

        val method = purchaseRepository.getMethod(
            id = "letsexchange_buy_swap",
            testnet = wallet.testnet,
            locale = settingsRepository.getLocale()
        )
        if (method != null) {
            val currency = api.getCurrencyCodeByCountry(settingsRepository)
            WalletPurchaseMethodEntity(
                method = method,
                wallet = wallet,
                currency = currency,
                config = api.config
            )
        } else {
            null
        }
    }
}