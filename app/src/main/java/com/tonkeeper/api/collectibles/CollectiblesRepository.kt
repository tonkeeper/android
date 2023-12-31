package com.tonkeeper.api.collectibles

import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.base.BaseAccountRepository
import com.tonkeeper.api.collectibles.db.CollectiblesDao
import com.tonkeeper.api.fromJSON
import io.tonapi.apis.AccountsApi
import io.tonapi.models.NftItem

class CollectiblesRepository(
    private val api: AccountsApi = Tonapi.accounts,
    private val dao: CollectiblesDao = App.db.collectiblesDao()
): BaseAccountRepository<NftItem>() {

    suspend fun getNftItemCache(
        nftAddress: String
    ): NftItem? {
        val data = dao.getItemData(nftAddress) ?: return null
        return fromJSON(data)
    }

    override suspend fun deleteCache(accountId: String) {
        dao.delete(accountId)
    }

    override suspend fun onCacheRequest(
        accountId: String
    ): List<NftItem> {
        return dao.get(accountId)
    }

    override fun find(
        value: String,
        items: List<NftItem>
    ): NftItem? {
        return items.find {
            it.address == value
        }
    }

    override fun onFetchRequest(
        accountId: String
    ): List<NftItem> {
        return api.getAccountNftItems(
            accountId = accountId,
            limit = 100,
            indirectOwnership = true,
        ).nftItems
    }

    override suspend fun insertCache(
        accountId: String,
        items: List<NftItem>
    ) {
        dao.insert(accountId, items)
    }

}