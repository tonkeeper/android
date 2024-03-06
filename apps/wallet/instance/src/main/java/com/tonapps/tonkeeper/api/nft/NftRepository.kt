package com.tonapps.tonkeeper.api.nft

import com.tonapps.wallet.api.Tonapi
import com.tonapps.wallet.api.SourceAPI
import com.tonapps.tonkeeper.api.fromJSON
import com.tonapps.tonkeeper.api.nft.db.NftDao
import com.tonapps.tonkeeper.api.nft.db.NftEntity
import com.tonapps.tonkeeper.api.withRetry
import io.tonapi.apis.NFTApi
import io.tonapi.models.NftItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NftRepository(
    private val api: SourceAPI<NFTApi> = Tonapi.nft,
    private val dao: NftDao = com.tonapps.tonkeeper.App.db.nftDao()
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