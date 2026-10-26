package com.tonapps.tonkeeper.ui.screen.settings.currency

import android.app.Application
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.settings.currency.list.Item
import com.tonapps.tonkeeper.worker.TotalBalancesWorker
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.CurrencyEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart

class CurrencyViewModel(
    app: Application,
    private val settings: SettingsRepository,
    private val api: API,
): BaseWalletVM(app) {

    private val remoteCurrenciesFlow: Flow<List<CurrencyEntity>> = flow {
        emit(api.getCurrencies(TonNetwork.MAINNET, settings.getLocale(), null))
    }.catch {
        emit(emptyList())
    }.onStart {
        emit(emptyList())
    }.flowOn(Dispatchers.IO)

    val uiItemsFlow: Flow<List<Item>> = combine(
        remoteCurrenciesFlow,
        settings.currencyFlow,
        ::buildUiItems
    )

    fun selectCurrency(entity: CurrencyEntity) {
        val currency = entity.asWalletCurrency() ?: return
        settings.currency = currency
        TotalBalancesWorker.run(context)
    }

    private fun buildUiItems(
        currencies: List<CurrencyEntity>,
        selected: WalletCurrency,
    ): List<Item> {
        val rows = currencies.mapNotNull { entity ->
            entity.asWalletCurrency()?.let { currency -> entity to currency }
        }.toMutableList()
        if (rows.none { (_, currency) -> currency == selected }) {
            rows.add(0, selected.asCurrencyEntity() to selected)
        }
        return rows.mapIndexed { index, (entity, currency) ->
            Item(
                currency = entity,
                selected = currency == selected,
                position = ListCell.getPosition(rows.size, index)
            )
        }
    }
}

private fun CurrencyEntity.asWalletCurrency(): WalletCurrency? {
    return if (isFiat) {
        WalletCurrency.fiatOrNull(code)
    } else {
        WalletCurrency.of(code)
    }
}

private fun WalletCurrency.asCurrencyEntity(): CurrencyEntity {
    return CurrencyEntity(
        code = code,
        name = title,
        image = null,
        symbol = null,
        type = when {
            isFiat -> CurrencyEntity.TYPE_FIAT
            else -> CurrencyEntity.TYPE_CRYPTO
        },
    )
}
