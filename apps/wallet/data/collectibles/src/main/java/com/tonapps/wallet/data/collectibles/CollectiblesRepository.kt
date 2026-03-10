package com.tonapps.wallet.data.collectibles

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.collectibles.entities.DnsExpiringEntity
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.collectibles.entities.NftListResult
import com.tonapps.wallet.data.collectibles.source.LocalDataSource
import io.extensions.renderType
import io.tonapi.models.TrustType
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flow

class CollectiblesRepository(
    private val context: Context,
    private val api: API
) {

    private val localDataSource: LocalDataSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalDataSource(context)
    }

    suspend fun getDnsExpiring(accountId: String, network: TonNetwork, period: Int) = run {
        api.getDnsExpiring(accountId, network, period).map { model ->
            DnsExpiringEntity(
                expiringAt = model.expiringAt,
                name = model.name,
                dnsItem = model.dnsItem?.let { NftEntity(it, network) }
            )
        }.sortedBy { it.daysUntilExpiration }
    }

    suspend fun getDnsSoonExpiring(accountId: String, network: TonNetwork, period: Int = 30) = getDnsExpiring(accountId, network, period)

    suspend fun getDnsNftExpiring(
        accountId: String,
        network: TonNetwork,
        nftAddress: String
    ) = getDnsExpiring(accountId, network, 366).firstOrNull {
        it.dnsItem?.address?.equalsAddress(nftAddress) == true
    }

    fun getNft(accountId: String, network: TonNetwork, address: String): NftEntity? {
        val nft = localDataSource.getSingle(accountId, network.isTestnet, address)
        if (nft != null) {
            return nft
        }
        return api.getNft(address, network)?.let { NftEntity(it, network) }
    }

    fun get(address: String, network: TonNetwork): List<NftEntity>? {
        val local = localDataSource.get(address, network.isTestnet)
        if (local.isEmpty()) {
            return getRemoteNftItems(address, network)
        }
        return local
    }

    fun getFlow(address: String, network: TonNetwork, isOnline: Boolean) = flow {
        try {
            val local = getLocalNftItems(address, network)
            if (local.isNotEmpty()) {
                emit(NftListResult(cache = true, list = local))
            }

            if (isOnline) {
                val remote = getRemoteNftItems(address, network) ?: return@flow
                emit(NftListResult(cache = false, list = remote))
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }.cancellable()

    private fun getLocalNftItems(
        address: String,
        network: TonNetwork
    ): List<NftEntity> {
        return localDataSource.get(address, network.isTestnet)
    }

    private fun getRemoteNftItems(
        address: String,
        network: TonNetwork
    ): List<NftEntity>? {
        val nftItems = api.getNftItems(address, network) ?: return null
        val items = nftItems.filter {
            it.trust != TrustType.blacklist && it.renderType != "hidden"
        }.map { NftEntity(it, network) }

        localDataSource.save(address, network.isTestnet, items.toList())
        return items
    }
}
