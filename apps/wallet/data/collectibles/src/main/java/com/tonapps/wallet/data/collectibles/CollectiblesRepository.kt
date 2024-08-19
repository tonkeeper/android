package com.tonapps.wallet.data.collectibles

import android.content.Context
import android.util.Log
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.collectibles.entities.NftListResult
import com.tonapps.wallet.data.collectibles.source.LocalDataSource
import com.tonapps.wallet.data.core.entity.DataEntity
import io.tonapi.models.TrustType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.Flow

class CollectiblesRepository(
    private val context: Context,
    private val api: API
) {


    private val localDataSource = LocalDataSource(context)

    suspend fun getNft(accountId: String, testnet: Boolean, address: String): NftEntity? {
        val nft = localDataSource.getSingle(accountId, testnet, address)
        if (nft != null) {
            return nft
        }
        return api.getNft(address, testnet)?.let { NftEntity(it, testnet) }
    }

    suspend fun get(address: String, testnet: Boolean): List<NftEntity>? {
        val local = localDataSource.get(address, testnet)
        if (local.isEmpty()) {
            return getRemoteNftItems(address, testnet)
        }
        return local
    }

    fun getFlow(address: String, testnet: Boolean, isOnline: Boolean) = flow {
        try {
            emit(NftListResult(cache = true))

            val local = getLocalNftItems(address, testnet)
            if (local.isNotEmpty()) {
                emit(NftListResult(cache = true, list = local))
            }

            if (isOnline) {
                val remote = getRemoteNftItems(address, testnet) ?: return@flow
                emit(NftListResult(cache = false, list = remote))
            }
        } catch (ignored: Throwable) { }
    }

    private fun getLocalNftItems(
        address: String,
        testnet: Boolean
    ): List<NftEntity> {
        return localDataSource.get(address, testnet)
    }

    private suspend fun getRemoteNftItems(
        address: String,
        testnet: Boolean
    ): List<NftEntity>? {
        val nftItems = api.getNftItems(address, testnet) ?: return null
        val items = nftItems.filter {
            it.trust != TrustType.blacklist && it.metadata["render_type"] != "hidden"
        }.map { NftEntity(it, testnet) }

        localDataSource.save(address, testnet, items.toList())
        return items
    }
}