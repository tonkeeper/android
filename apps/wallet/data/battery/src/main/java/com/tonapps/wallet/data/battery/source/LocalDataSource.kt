package com.tonapps.wallet.data.battery.source

import android.content.Context
import androidx.core.content.edit
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.extensions.prefs
import com.tonapps.security.Security
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.core.BlobDataSource
import org.ton.api.pub.PublicKeyEd25519

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

    fun setBalance(publicKey: PublicKeyEd25519, network: TonNetwork, entity: BatteryBalanceEntity) {
        balance.setCache(balanceCacheKey(publicKey, network), entity)
    }

    fun getBalance(publicKey: PublicKeyEd25519, network: TonNetwork): BatteryBalanceEntity? {
        return balance.getCache(balanceCacheKey(publicKey, network))
    }

    private fun balanceCacheKey(publicKey: PublicKeyEd25519, network: TonNetwork): String {
        return "${network.name.lowercase()}:${publicKey.hex()}"
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

