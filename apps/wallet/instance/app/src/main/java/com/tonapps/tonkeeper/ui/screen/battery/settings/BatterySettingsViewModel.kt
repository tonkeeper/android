package com.tonapps.tonkeeper.ui.screen.battery.settings

import android.app.Application
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.battery.settings.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class BatterySettingsViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val batteryRepository: BatteryRepository,
    private val api: API,
) : BaseWalletVM(app) {

    val tronUsdtEnabled: Boolean
        get() = settingsRepository.getTronUsdtEnabled(wallet.id)

    val titleFlow = batteryRepository.balanceUpdatedFlow.map { _ ->
        val batteryBalance = getBatteryBalance(wallet)
        val hasBalance = batteryBalance.balance.isPositive

        if (hasBalance) {
            ""
        } else {
            getString(Localization.transactions)
        }
    }.flowOn(Dispatchers.IO)

    val uiItemsFlow = batteryRepository.balanceUpdatedFlow.map { _ ->
        val config = api.config
        val batteryBalance = getBatteryBalance(wallet)
        val hasBalance = batteryBalance.balance.isPositive

        val uiItems = mutableListOf<Item>()
        if (hasBalance) {
            uiItems.add(Item.SettingsHeader)
        }

        val types = BatteryTransaction.entries

        val size = if (tronUsdtEnabled) {
            types.size + 1
        } else {
            types.size
        }

        for ((index, type) in types.withIndex()) {
            val position = ListCell.getPosition(size, index)
            val meanPrice = getTransactionMeanPrice(config, type)
            val charges = BatteryMapper.calculateChargesAmount(meanPrice, config.batteryMeanFees)
            val enabled = settingsRepository.batteryIsEnabledTx(wallet.accountId, type)
            val item = Item.SupportedTransaction(
                wallet = wallet,
                position = position,
                supportedTransaction = type,
                enabled = enabled,
                showToggle = hasBalance,
                changes = charges,
            )
            uiItems.add(item)
        }

        if (tronUsdtEnabled) {
            val minCharges = BatteryMapper.calculateChargesAmount(config.batteryMeanPriceTrcMin, config.batteryMeanFees)
            val maxCharges = BatteryMapper.calculateChargesAmount(config.batteryMeanPriceTrcMax, config.batteryMeanFees)
            uiItems.add(
                Item.SupportedTransaction(
                    wallet = wallet,
                    position = ListCell.Position.LAST,
                    supportedTransaction = BatteryTransaction.TRC20,
                    enabled = true,
                    showToggle = hasBalance,
                    changes = 0,
                    changesRange = minCharges to maxCharges,
                )
            )
        }

        uiItems.toList()
    }.flowOn(Dispatchers.IO)

    private suspend fun getBatteryBalance(
        wallet: WalletEntity
    ): BatteryBalanceEntity {
        val tonProofToken =
            accountRepository.requestTonProofToken(wallet) ?: return BatteryBalanceEntity.Empty
        return batteryRepository.getBalance(
            tonProofToken = tonProofToken,
            publicKey = wallet.publicKey,
            testnet = wallet.testnet
        )
    }

    private fun getTransactionMeanPrice(
        config: ConfigEntity,
        transaction: BatteryTransaction
    ): String {
        return when (transaction) {
            BatteryTransaction.NFT -> config.batteryMeanPriceNft
            BatteryTransaction.SWAP -> config.batteryMeanPriceSwap
            BatteryTransaction.JETTON -> config.batteryMeanPriceJetton
            else -> "0"
        }
    }
}