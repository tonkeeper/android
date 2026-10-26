package com.tonapps.tonkeeper.ui.screen.battery.settings

import android.app.Application
import androidx.annotation.PluralsRes
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.log.L
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.battery.settings.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
        val batteryBalance = getBatteryBalance(wallet)
        val batteryConfig = getBatteryConfig(wallet)
        val hasBalance = batteryBalance.balance.isPositive

        val uiItems = mutableListOf<Item>()
        if (hasBalance) {
            uiItems.add(Item.SettingsHeader)
        }

        val types = BatteryTransaction.entries

        val size = types.size + 1

        for ((index, type) in types.withIndex()) {
            val position = ListCell.getPosition(size, index)
            val meanPrice = getTransactionMeanPrice(batteryConfig.meanPrices, type)
            val enabled = settingsRepository.batteryIsEnabledTx(wallet.accountId, type)
            val item = Item.SupportedTransaction(
                wallet = wallet,
                position = position,
                supportedTransaction = type,
                enabled = enabled,
                showToggle = hasBalance,
                changes = meanPrice,
                chargesRes = chargesRes(type),
            )
            uiItems.add(item)
        }

        uiItems.add(
            Item.SupportedTransaction(
                wallet = wallet,
                position = ListCell.Position.LAST,
                supportedTransaction = BatteryTransaction.TRC20,
                enabled = true,
                showToggle = hasBalance,
                changes = batteryConfig.meanPrices.batteryMeanPriceTronUsdt ?: 0,
                chargesRes = Plurals.battery_charges_per_action,
            )
        )

        supportedNetworks()?.let { uiItems.add(Item.Networks(it)) }

        uiItems.toList()
    }.flowOn(Dispatchers.IO)

    private suspend fun getBatteryBalance(
        wallet: WalletEntity
    ): BatteryBalanceEntity {
        return batteryRepository.getBalance(wallet)
    }

    private suspend fun getBatteryConfig(
        wallet: WalletEntity
    ): BatteryConfigEntity {
        return batteryRepository.getConfig(wallet.network)
    }

    // The battery pays EVM fees for a multichain wallet only: a legacy one keeps the TON prices as they are.
    // There the mean prices are TON's, and a transfer or a swap costs another number of charges elsewhere.
    @PluralsRes
    private fun chargesRes(transaction: BatteryTransaction): Int = when {
        !wallet.isMultichain -> Plurals.battery_charges_per_action
        transaction == BatteryTransaction.JETTON -> Plurals.battery_charges_per_transfer_in_ton
        transaction == BatteryTransaction.SWAP -> Plurals.battery_charges_per_swap_in_ton
        else -> Plurals.battery_charges_per_action
    }

    // TON and TRON, then the EVM networks the relayer's config carries; none when it cannot be read.
    private suspend fun supportedNetworks(): String? {
        if (!wallet.isMultichain) {
            return null
        }
        val evmChainIds = try {
            batteryRepository.getGaslessChains(wallet.network)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            emptySet()
        }
        val evmNames = evmChainIds.mapNotNull { chainId ->
            val (label, net) = chainId.split('/').takeIf { it.size == 2 } ?: return@mapNotNull null
            Chain.find(label, net)?.takeIf { it.isEvm }?.name
        }.distinct()
        if (evmNames.isEmpty()) {
            return null
        }
        val names = listOf(Chain.Ton.Mainnet.name, Chain.Tron.Mainnet.name) + evmNames
        val list = getString(Localization.new_networks_list_last, names.dropLast(1).joinToString(", "), names.last())
        return getString(Localization.battery_supported_networks, list)
    }

    private fun getTransactionMeanPrice(
        meanPrices: BatteryConfigEntity.MeanPrices,
        transaction: BatteryTransaction
    ): Int {
        return when (transaction) {
            BatteryTransaction.NFT -> meanPrices.batteryMeanPriceNft
            BatteryTransaction.SWAP -> meanPrices.batteryMeanPriceSwap
            BatteryTransaction.JETTON -> meanPrices.batteryMeanPriceJetton
            else -> 0
        }
    }
}