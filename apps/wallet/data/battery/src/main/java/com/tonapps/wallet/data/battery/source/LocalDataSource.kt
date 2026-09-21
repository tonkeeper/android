package com.tonapps.wallet.data.battery.source

import android.content.Context
import androidx.core.content.edit
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.extensions.prefs
import com.tonapps.security.Security
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.core.BlobDataSource

internal class LocalDataSource(
    context: Context
) {

    companion object {
        private const val NAME = "battery"
    }

    private val balance = BlobDataSource.simple<BatteryBalanceEntity>(context, "battery_balance")
    private val configStore = BlobDataSource.simple<BatteryConfigEntity>(context, "battery_config")

    private val prefs = context.prefs(NAME)

    fun setConfig(network: TonNetwork, entity: BatteryConfigEntity) {
        configStore.setCache(configCacheKey(network), entity)
    }

    fun getConfig(network: TonNetwork): BatteryConfigEntity? {
        return configStore.getCache(configCacheKey(network))
    }

    private fun configCacheKey(network: TonNetwork): String {
        return network.name.lowercase()
    }

    fun setBalance(wallet: WalletEntity, entity: BatteryBalanceEntity) {
        balance.setCache(balanceCacheKey(wallet), entity)
    }

    fun getBalance(wallet: WalletEntity): BatteryBalanceEntity? {
        return balance.getCache(balanceCacheKey(wallet))
    }

    // Multichain wallets are keyed by wallet id, the same identity their battery authorization uses.
    // Their publicKey is unreliable here: an entity that wasn't resolved through
    // UnifiedAccountRepository carries the empty key, which would collide across every MC wallet.
    private fun balanceCacheKey(wallet: WalletEntity): String {
        if (wallet.type == WalletType.Multichain) {
            return "wallet:${wallet.id}"
        }
        return "${wallet.network.name.lowercase()}:${wallet.publicKey.hex()}"
    }

    fun getAppliedPromo(network: TonNetwork): String? {
        return prefs.getString(promoKey(network), null)
    }

    fun setAppliedPromo(network: TonNetwork, promo: String?) {
        prefs.edit {
            putString(promoKey(network), promo)
        }
    }

    private fun promoKey(network: TonNetwork): String {
        return "promo_${network.name.lowercase()}"
    }
}

