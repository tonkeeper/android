package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency

import android.app.Application
import android.net.Uri
import android.text.SpannableStringBuilder
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.facebook.common.util.UriUtil
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.mapList
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.api.getCurrencyCodeByCountry
import com.tonapps.tonkeeper.extensions.getNormalizeCountryFlow
import com.tonapps.tonkeeper.extensions.onRampDataFlow
import com.tonapps.tonkeeper.extensions.onRampFormCurrencyFlow
import com.tonapps.tonkeeper.manager.assets.AssetsManager
import com.tonapps.tonkeeper.os.AndroidCurrency
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.purchase.entity.OnRampCurrencies
import com.tonapps.wallet.data.purchase.entity.TONAssetsEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import uikit.extensions.badgeAccentColor
import uikit.extensions.withBlueBadge
import kotlin.math.acos

class OnRampPickerViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val send: Boolean,
    private val settingsRepository: SettingsRepository,
    private val purchaseRepository: PurchaseRepository,
    private val tokenRepository: TokenRepository,
    private val assetsManager: AssetsManager,
    private val api: API
): BaseWalletVM(app) {

    private val formCurrencyFlow = purchaseRepository.onRampFormCurrencyFlow(settingsRepository)

    private val onRampResultFlow = purchaseRepository
        .onRampDataFlow(settingsRepository, api)

    private val onRampFlow = onRampResultFlow.map { it.data }

    private val currencyByCountry: WalletCurrency
        get() {
            val code = AndroidCurrency.resolve(settingsRepository.country)?.currencyCode ?: "USA"
            return WalletCurrency.ofOrDefault(code)
        }

    private val _uiCommandFlow = MutableEffectFlow<OnRampPickerCommand>()
    val uiCommandFlow = _uiCommandFlow.asSharedFlow()

    private val uiItemsTONAssetsFlow = onRampFlow.map {
        val assets = it.tonAssets
        if (send) assets.input else assets.output
    }.map { jettonAddress ->
        val uiItems = mutableListOf<Item.Currency>()
        if (send) {
            val balances = assetsManager.getTokens(wallet, jettonAddress).sortedBy { it.fiat }.asReversed()
            for ((index, balance) in balances.withIndex()) {
                val position = ListCell.getPosition(balances.size, index)
                val format = CurrencyFormatter.format(settingsRepository.currency.code, balance.fiat)
                val code = balance.token.symbol
                if (balance.token.isUsdt) {
                    code.withBlueBadge(context, Localization.ton)
                }
                uiItems.add(Item.Currency(
                    token = balance.token.token,
                    position = position,
                    title = format,
                    code = code
                ))
            }
        } else {
            uiItems.add(Item.Currency(TokenEntity.TON, ListCell.Position.FIRST))
            uiItems.add(Item.Currency(TokenEntity.USDT, ListCell.Position.MIDDLE))
            val tokens = tokenRepository.getTokens(wallet.testnet, jettonAddress).toMutableList()
            tokens.removeIf { it.isTon || it.isUsdt }
            for (token in tokens) {
                uiItems.add(Item.Currency(token, ListCell.Position.MIDDLE))
            }
        }
        uiItems.toList()
    }.flowOn(Dispatchers.IO)

    private val tonAssetsFlow = uiItemsTONAssetsFlow.mapList { it.currency }

    private val _searchQueryFlow = MutableStateFlow("")
    val searchQueryFlow = _searchQueryFlow.asSharedFlow().map { it.trim() }

    val uiItemsFlow = combine(
        formCurrencyFlow,
        onRampFlow,
        uiItemsTONAssetsFlow,
        searchQueryFlow,
    ) { (sendCurrency, receiveCurrency), supportedCurrencies, tonAssets, searchQuery ->
        val selectedCurrency = if (send) sendCurrency else receiveCurrency
        val list = mutableListOf<Item>()
        list.addAll(buildFiatBlock(selectedCurrency, supportedCurrencies.fiat, searchQuery))
        list.addAll(buildTONAssetsBlock(selectedCurrency, tonAssets, searchQuery))
        list.addAll(buildExternalBlock(selectedCurrency, supportedCurrencies.externalCurrency, searchQuery))
        list.toList()
    }

    init {
        pushCommand(OnRampPickerCommand.Main)
    }

    fun setSearchQuery(query: String) {
        _searchQueryFlow.tryEmit(query)
    }

    fun openFiatPicker() {
        onRampFlow
            .take(1)
            .map { it.availableFiatSlugs }
            .map { codes ->
                codes.mapNotNull { WalletCurrency.of(it) }
            }.collectFlow(::openCurrencyPicker)
    }

    fun openTONAssetsPicker() {
        tonAssetsFlow
            .take(1)
            .collectFlow(::openCurrencyPicker)
    }

    fun openCryptoPicker() {
        onRampFlow
            .take(1)
            .map { it.externalCurrency }
            .collectFlow(::openCurrencyPicker)
    }

    private fun openCurrencyPicker(currencies: List<WalletCurrency>) {
        pushCommand(OnRampPickerCommand.OpenCurrencyPicker(currencies))
    }

    fun close() {
        pushCommand(OnRampPickerCommand.Finish)
    }

    private fun pushCommand(command: OnRampPickerCommand) {
        _uiCommandFlow.tryEmit(command)
    }

    private fun fiatCurrency(selectedCurrency: WalletCurrency, query: String): List<WalletCurrency> {
        return if (query.isEmpty()) {
            mutableListOf<WalletCurrency>().apply {
                if (selectedCurrency.fiat) {
                    add(selectedCurrency)
                }
                add(currencyByCountry)
            }.distinct()
        } else {
            WalletCurrency.FIAT.mapNotNull { WalletCurrency.of(it) }.filter {
                it.containsQuery(query)
            }.take(3)
        }
    }

    private fun fiatMethodIcons(keys: List<String>): List<Uri> {
        val icons = getCurrencyIcons(keys, settingsRepository.country.equals("ru", ignoreCase = true))
        return icons.map { UriUtil.getUriForResourceId(it) }
    }

    private fun buildFiatBlock(
        selectedCurrency: WalletCurrency,
        supportedFiat: OnRampCurrencies,
        query: String
    ): List<Item> {
        val currencies = WalletCurrency.sort(WalletCurrency.FIAT.mapNotNull { WalletCurrency.of(it) })
        val previewCurrencies = fiatCurrency(selectedCurrency, query)

        val list = mutableListOf<Item>()
        for ((index, currency) in previewCurrencies.withIndex()) {
            val position = if (query.isNotEmpty()) {
                ListCell.getPosition(previewCurrencies.size, index)
            } else if (index == 0) {
                ListCell.Position.FIRST
            } else {
                ListCell.Position.MIDDLE
            }
            val selected = selectedCurrency == currency
            list.add(Item.Currency(currency, position, selected))
        }
        if (query.isEmpty() && list.size > 0) {
            list.add(Item.More(
                id = ALL_CURRENCIES_ID,
                title = getString(Localization.all_currencies),
                values = currencies.filterNot { it in previewCurrencies }.take(2),
            ))
        }

        if (list.size > 0) {
            val titleItem = Item.Title(getString(Localization.currency), fiatMethodIcons(if (send) supportedFiat.inputs else supportedFiat.outputs), true)
            list.add(0, titleItem)
        }

        return list.toList()
    }

    private fun buildTONAssetsBlock(
        selectedCurrency: WalletCurrency,
        tonAssets: List<Item.Currency>,
        query: String
    ): List<Item> {
        val max = if (send) {
            if (tonAssets.size == 4) 4 else 3
        } else {
            2
        }

        val assets = if (query.isEmpty()) {
            if (tonAssets.size == 4) {
                tonAssets
            } else {
                tonAssets.take(max)
            }
        } else {
            tonAssets.filter {
                it.contains(query)
            }.take(3)
        }

        var list = mutableListOf<Item>()
        for ((index, asset) in assets.withIndex()) {
            val selected = asset.currency == selectedCurrency
            val position = if (query.isNotEmpty()) {
                ListCell.getPosition(assets.size, index)
            } else if (index == 0) {
                ListCell.Position.FIRST
            } else {
                ListCell.Position.MIDDLE
            }
            list.add(asset.copy(
                position = position,
                selected = selected
            ))
        }

        if (query.isEmpty() && tonAssets.size > max) {
            list = list.take(max + 1).toMutableList()
            list.add(Item.More(
                id = ALL_TON_ASSETS_ID,
                title = getString(Localization.all_ton_assets),
                values = tonAssets.drop(max).map { it.currency },
            ))
        }

        if (list.isNotEmpty()) {
            list.add(0, Item.Title(getString(Localization.ton_assets)))
        }

        return list.toList()
    }

    private fun buildExternalBlock(
        selectedCurrency: WalletCurrency,
        currencies: List<WalletCurrency>,
        query: String
    ): List<Item> {
        val list = mutableListOf<Item>()

        val previewCurrencies = currencies.filter {
            if (query.isEmpty()) {
                true
            } else {
                it.containsQuery(query)
            }
        }.take(2)

        for ((index, currency) in previewCurrencies.withIndex()) {
            val position = if (query.isNotEmpty()) {
                ListCell.getPosition(previewCurrencies.size, index)
            } else if (index == 0) {
                ListCell.Position.FIRST
            } else {
                ListCell.Position.MIDDLE
            }
            val selected = selectedCurrency == currency
            list.add(Item.Currency(currency, position, selected))
        }

        if (query.isEmpty() && list.size > 0) {
            list.add(Item.More(
                id = ALL_CRYPTO_ID,
                title = getString(Localization.all_assets),
                values = currencies.filterNot { it in previewCurrencies || it.isUSDT }.take(2),
            ))
        }

        if (list.size > 0) {
            list.add(0, Item.Title(getString(Localization.crypto)))
        }

        return list.toList()
    }

    fun setToken(address: String, network: String) {
        if (network.equals("TON", ignoreCase = true)) {
            setJetton(address)
        }
    }

    private fun setJetton(address: String) {
        viewModelScope.launch {
            val token = tokenRepository.getToken(wallet.accountId, wallet.testnet, address) ?: return@launch
            val currency = WalletCurrency.of(token.address) ?: WalletCurrency(
                code = token.symbol,
                title = token.name,
                chain = WalletCurrency.Chain.TON(token.address, token.decimals),
                iconUrl = token.imageUri.toString()
            )
            setCurrency(currency)
        }
    }

    fun setCurrency(currency: WalletCurrency) {
        if (send) {
            purchaseRepository.sendCurrency = currency
        } else {
            purchaseRepository.receiveCurrency = currency
        }
        close()
    }

    companion object {

        const val ALL_CURRENCIES_ID = "all_currencies"
        const val ALL_TON_ASSETS_ID = "all_ton_assets"
        const val ALL_CRYPTO_ID = "all_crypto"

        private fun getCardsIcons(rus: Boolean): List<Int> {
            val icons = mutableListOf(R.drawable.visa, R.drawable.mastercard)
            if (rus) {
                icons.add(R.drawable.mir)
            }
            return icons.toList()
        }

        private fun getCurrencyIcons(keys: List<String>, rus: Boolean): List<Int> {
            val icons = mutableListOf<Int>()
            for (key in keys) {
                when (key) {
                    "card" -> icons.addAll(getCardsIcons(rus))
                    "google_pay" -> icons.add(R.drawable.google_pay)
                    "paypal" -> icons.add(R.drawable.paypal)
                }
            }
            return icons.toList()
        }
    }
}