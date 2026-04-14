package com.tonapps.wallet.data.rates.source

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.wallet.data.core.BlobDataSource
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.rates.entity.RateEntity
import com.tonapps.wallet.data.rates.entity.RatesEntity
import java.util.concurrent.TimeUnit

internal class BlobDataSource(context: Context): BlobDataSource<RatesEntity>(
    context = context,
    path = "rates",
    timeout = TimeUnit.HOURS.toMillis(12)
) {

    override fun onUnmarshall(bytes: ByteArray) = bytes.toParcel<RatesEntity>()

    override fun onMarshall(data: RatesEntity) = data.toByteArray()

    private fun cacheKey(network: TonNetwork, currency: WalletCurrency): String {
        return "${network.value}_${currency.code}"
    }

    fun get(network: TonNetwork, currency: WalletCurrency): RatesEntity {
        val key = cacheKey(network, currency)
        val rates = getCache(key) ?: RatesEntity.empty(currency)
        if (rates.isEmpty) {
            clearCache(key)
            return rates
        }
        return rates.copy()
    }

    fun add(network: TonNetwork, currency: WalletCurrency, list: List<RateEntity>) {
        if (list.isEmpty()) {
            return
        }
        val rates = get(network, currency).merge(list)
        setCache(cacheKey(network, currency), rates)
    }

}