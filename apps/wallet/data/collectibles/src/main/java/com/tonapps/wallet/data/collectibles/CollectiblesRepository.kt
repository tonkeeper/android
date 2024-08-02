package com.tonapps.wallet.data.collectibles

import android.content.Context
import android.util.Log
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.collectibles.source.LocalDataSource
import io.tonapi.models.TrustType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CollectiblesRepository(
    private val context: Context,
    private val api: API
) {

    private val localDataSource = LocalDataSource(context)

    fun getNft(accountId: String, testnet: Boolean, address: String): NftEntity? {
        val nft = localDataSource.getSingle(accountId, testnet, address)
        if (nft != null) {
            return nft
        }
        return api.getNft(address, testnet)?.let {  NftEntity(it, testnet) }
    }

    fun getLocalNftItems(
        address: String,
        testnet: Boolean
    ): List<NftEntity> {
        return localDataSource.get(address, testnet)
    }

    suspend fun getRemoteNftItems(
        address: String,
        testnet: Boolean
    ): List<NftEntity> {
        val items = api.getNftItems(address, testnet).filter {
            it.trust != TrustType.blacklist
        }.map { NftEntity(it, testnet) }
        localDataSource.save(address, testnet, items)
        return items
    }
}