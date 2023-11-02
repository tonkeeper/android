package com.tonkeeper.api.nft

import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.fromJSON
import com.tonkeeper.api.jetton.db.JettonDao
import com.tonkeeper.api.nft.db.NftDao
import com.tonkeeper.api.nft.db.NftEntity
import com.tonkeeper.api.userLikeAddress
import io.tonapi.apis.NFTApi
import io.tonapi.models.NftItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NftRepository(
    private val api: NFTApi = Tonapi.nft,
    private val dao: NftDao = App.db.nftDao()
) {

    suspend fun getItem(
        address: String
    ): NftItem = withContext(Dispatchers.IO) {
        val userLikeAddress = address.userLikeAddress
        val cached = fromCache(userLikeAddress)
        if (cached != null) {
            return@withContext cached
        }
        val cloud = fromCloud(userLikeAddress)
        dao.insert(NftEntity(cloud))
        cloud
    }

    private suspend fun fromCache(
        address: String
    ): NftItem? = withContext(Dispatchers.IO) {
        dao.get(address)?.let { fromJSON(it.data) }
    }

    private suspend fun fromCloud(
        address: String
    ): NftItem = withContext(Dispatchers.IO) {
        api.getNftItemByAddress(address.userLikeAddress)
    }
}