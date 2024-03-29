package com.tonkeeper.api.nft

import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.base.SourceAPI
import com.tonkeeper.api.fromJSON
import com.tonkeeper.api.nft.db.NftDao
import com.tonkeeper.api.nft.db.NftEntity
import com.tonkeeper.api.withRetry
import io.tonapi.apis.NFTApi
import io.tonapi.models.NftItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NftRepository(
    private val api: SourceAPI<NFTApi> = Tonapi.nft,
    private val dao: NftDao = App.db.nftDao()
) {

    suspend fun getItem(
        address: String,
        testnet: Boolean,
    ): NftItem? = withContext(Dispatchers.IO) {
        val cached = fromCache(address)
        if (cached != null) {
            return@withContext cached
        }
        val cloud = fromCloud(address, testnet) ?: return@withContext null
        dao.insert(NftEntity(cloud))
        cloud
    }

    private suspend fun fromCache(
        address: String
    ): NftItem? = withContext(Dispatchers.IO) {
        dao.get(address)?.let { fromJSON(it) }
    }

    private suspend fun fromCloud(
        address: String,
        testnet: Boolean,
    ): NftItem? = withContext(Dispatchers.IO) {
        withRetry {
            api.get(testnet).getNftItemByAddress(address)
        }
    }
}