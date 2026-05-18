package com.tonapps.tonkeeper.ui.screen.tronfees

import android.app.Application
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.tronfees.list.Item
import com.tonapps.deposit.usecase.emulation.EmulationUseCase
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.deposit.usecase.emulation.TronFeesEmulation
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Plurals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn

class TronFeesViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val type: TronFeesScreenType,
    private val emulationUseCase: EmulationUseCase,
    private val settingsRepository: SettingsRepository,
    private val api: API,
) : BaseWalletVM(app) {

    private val emulationFlow = MutableStateFlow<TronFeesEmulation?>(null)

    fun setEmulation(value: TronFeesEmulation?) {
        emulationFlow.tryEmit(value)
    }

    private val disableBattery: Boolean
        get() = api.getConfig(wallet.network).flags.disableBattery

    val uiItemsFlow: Flow<List<Item>> = combine(
        settingsRepository.currencyFlow
            .filterNotNull(), emulationFlow
    ) { currency, emulation ->
        val fees = emulationUseCase.getTrc20TransferDefaultFees(wallet, currency, emulation)

        val list = mutableListOf<Item>()

        list.add(
            Item.Header(
                screenType = type,
                onlyTrx = disableBattery
            )
        )

        if (!disableBattery) {
            list.add(
                Item.Battery(
                    position = ListCell.Position.FIRST,
                    wallet = wallet,
                    amountFormat = context.resources.getQuantityString(
                        Plurals.battery_charges,
                        fees.batteryFee.charges,
                        CurrencyFormatter.format(value = fees.batteryFee.charges.toBigDecimal())
                    ),
                    balanceFormat = context.resources.getQuantityString(
                        Plurals.battery_charges,
                        fees.batteryFee.balance,
                        CurrencyFormatter.format(value = fees.batteryFee.balance.toBigDecimal())
                    )
                )
            )
            list.add(
                Item.Token(
                    position = ListCell.Position.MIDDLE,
                    wallet = wallet,
                    token = TokenEntity.TON,
                    amountFormat = if (emulation != null) {
                        CurrencyFormatter.formatFull(
                            TokenEntity.TON.symbol,
                            fees.tonFee.amount,
                            TokenEntity.TON.decimals
                        )
                    } else {
                        CurrencyFormatter.format(
                            TokenEntity.TON.symbol,
                            fees.tonFee.amount
                        )
                    },
                    balanceFormat = if (emulation != null) {
                        CurrencyFormatter.formatFull(
                            TokenEntity.TON.symbol,
                            fees.tonFee.balance,
                            TokenEntity.TON.decimals
                        )
                    } else {
                        CurrencyFormatter.format(
                            TokenEntity.TON.symbol,
                            fees.tonFee.balance
                        )
                    },
                )
            )
        }

        list.add(
            Item.Token(
                position = if (disableBattery) ListCell.Position.SINGLE else ListCell.Position.LAST,
                wallet = wallet,
                token = TokenEntity.TRX,
                amountFormat = CurrencyFormatter.format(
                    TokenEntity.TRX.symbol,
                    fees.trxFee.amount
                ),
                balanceFormat = CurrencyFormatter.format(
                    TokenEntity.TRX.symbol,
                    fees.trxFee.balance
                ),
                transfersCount = if (disableBattery) fees.trxFee.availableTransfers else null
            )
        )

        list.add(Item.LearnMore(url = api.getConfig(wallet.network).tronFeeFaqUrl))

        list
    }
        .flowOn(Dispatchers.IO)
}
