package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.extensions.getUriForResourceId
import com.tonapps.tonkeeper.manager.assets.AssetsManager
import com.tonapps.tonkeeper.os.AndroidCurrency
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.purchase.entity.OnRampCurrencies
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import uikit.extensions.withBlueBadge

class OnRampPickerViewModel(
    app: Application,
    private val args: OnRampPickerArgs,
    private val wallet: WalletEntity,
    private val settingsRepository: SettingsRepository,
    private val purchaseRepository: PurchaseRepository,
    private val tokenRepository: TokenRepository,
    private val assetsManager: AssetsManager,
    private val api: API,
    private val environment: Environment,
): BaseWalletVM(app) {

    private val onRampFlow = purchaseRepository.onRampDataFlow()

    private val currencyByCountry: WalletCurrency
        get() {
            val code = AndroidCurrency.resolve(environment.deviceCountry)?.currencyCode ?: "US"
            return WalletCurrency.ofOrDefault(code)
        }

    private val _uiCommandFlow = MutableEffectFlow<OnRampPickerCommand>()
    val uiCommandFlow = _uiCommandFlow.asSharedFlow()

    private val uiItemsTONAssetsFlow = onRampFlow.map {
        val assets = it.tonAssets
        if (args.send) assets.input else assets.output
    }.map { jettonAddress ->
        val uiItems = mutableListOf<Item.Currency>()
        if (args.send) {
            val balances = assetsManager.getTokens(wallet, jettonAddress).sortedBy {
                it.fiat
            }.filter { jettonAddress.contains(it.address) && it.blockchain == Blockchain.TON }.asReversed()
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
            val tokens = tokenRepository.getTokens(wallet.network, jettonAddress).toMutableList()
            tokens.removeIf { it.isTon || it.isUsdt }
            for (token in tokens) {
                uiItems.add(Item.Currency(token, ListCell.Position.MIDDLE))
            }
        }
        uiItems.toList()
    }.flowOn(Dispatchers.IO)

    private val _searchQueryFlow = MutableStateFlow("")
    val searchQueryFlow = _searchQueryFlow.asSharedFlow().map { it.trim() }

    val uiItemsFlow = combine(
        onRampFlow,
        uiItemsTONAssetsFlow,
        searchQueryFlow,
    ) { supportedCurrencies, tonAssets, searchQuery ->
        val list = mutableListOf<Item>()
        list.addAll(buildFiatBlock(args.currency, supportedCurrencies.fiat, searchQuery))
        list.addAll(buildTONAssetsBlock(args.currency, tonAssets, searchQuery))
        list.addAll(buildExternalBlock(args.currency, supportedCurrencies.externalCurrency, searchQuery))
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
            .map { (it.availableFiatSlugs + WalletCurrency.FIAT).distinct() }
            .map { codes ->
                codes.mapNotNull { WalletCurrency.of(it) }
            }.collectFlow {
                openCurrencyPicker(Localization.currency, it)
            }
    }

    fun openTONAssetsPicker() {
        uiItemsTONAssetsFlow
            .take(1)
            .collectFlow { assets ->
                val currencies = assets.map { it.currency }
                openCurrencyPicker(Localization.ton_assets, currencies, assets.map { it.title.toString() })
            }
    }

    fun openCryptoPicker() {
        onRampFlow
            .take(1)
            .map { it.externalCurrency }
            .collectFlow {
                openCurrencyPicker(Localization.crypto, it)
            }
    }

    private fun openCurrencyPicker(localization: Int, currencies: List<WalletCurrency>, extras: List<String> = emptyList()) {
        pushCommand(OnRampPickerCommand.OpenCurrencyPicker(localization, currencies, extras))
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
            }.distinctBy { it.code }
        } else {
            WalletCurrency.FIAT.mapNotNull { WalletCurrency.of(it) }.filter {
                it.containsQuery(query)
            }.take(5)
        }
    }

    private fun fiatMethodIcons(keys: List<String>): List<Uri> {
        val icons = getCurrencyIcons(keys, environment.deviceCountry.equals("ru", ignoreCase = true))
        return icons.map { getUriForResourceId(it) }
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
        if (query.isEmpty() && list.isNotEmpty()) {
            list.add(Item.More(
                id = ALL_CURRENCIES_ID,
                title = getString(Localization.all_currencies),
                values = currencies.filterNot { it in previewCurrencies }.take(2),
            ))
        }

        if (list.isNotEmpty()) {
            val titleItem = Item.Title(getString(Localization.currency), fiatMethodIcons(if (args.send) supportedFiat.inputs else supportedFiat.outputs), true)
            list.add(0, titleItem)
        }

        return list.toList()
    }

    private fun buildTONAssetsBlock(
        selectedCurrency: WalletCurrency,
        tonAssets: List<Item.Currency>,
        query: String
    ): List<Item> {
        val max = if (args.send) {
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
            }.take(30)
        }

        val moreItem = if (query.isEmpty() && tonAssets.size > max) {
            Item.More(
                id = ALL_TON_ASSETS_ID,
                title = getString(Localization.all_ton_assets),
                values = tonAssets.drop(max).map { it.currency },
            )
        } else {
            null
        }

        var list = mutableListOf<Item>()
        for ((index, asset) in assets.withIndex()) {
            val selected = asset.currency == selectedCurrency
            val position = if (query.isNotEmpty()) {
                ListCell.getPosition(assets.size, index)
            } else if (index == 0) {
                ListCell.Position.FIRST
            } else if (moreItem == null && index == assets.size - 1) {
                ListCell.Position.LAST
            } else {
                ListCell.Position.MIDDLE
            }
            list.add(asset.copy(
                position = position,
                selected = selected
            ))
        }

        if (moreItem != null) {
            list = list.take(max + 1).toMutableList()
            list.add(moreItem)
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
        val max = if (query.isEmpty()) 2 else 50
        val list = mutableListOf<Item>()

        val previewCurrencies = currencies.filter {
            if (query.isEmpty()) {
                true
            } else {
                it.containsQuery(query)
            }
        }.take(max)

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

        if (query.isEmpty() && list.isNotEmpty()) {
            list.add(Item.More(
                id = ALL_CRYPTO_ID,
                title = getString(Localization.all_assets),
                values = currencies.filterNot { it in previewCurrencies || it.isUSDT }.take(2),
            ))
        }

        if (list.isNotEmpty()) {
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
            val token = tokenRepository.getToken(wallet.accountId, wallet.network, address) ?: return@launch
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
        pushCommand(OnRampPickerCommand.SetCurrency(currency))
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