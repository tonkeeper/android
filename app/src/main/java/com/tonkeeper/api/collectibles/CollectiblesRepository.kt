package com.tonkeeper.api.nft

import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.base.BaseAccountRepository
import com.tonkeeper.api.nft.db.NftDao
import io.tonapi.apis.AccountsApi
import io.tonapi.models.NftItem

class NftRepository(
    private val api: AccountsApi = Tonapi.accounts,
    private val dao: NftDao = App.db.nftDao()
): BaseAccountRepository<NftItem>() {

    override suspend fun fromCache(
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

    override fun fromCloud(
        accountId: String
    ): List<NftItem> {
        return api.getAccountNftItems(
            accountId = accountId,
            limit = 100,
        ).nftItems
    }

    override suspend fun clearCache(accountId: String) {
        dao.delete(accountId)
    }

    override suspend fun insertCache(
        accountId: String,
        items: List<NftItem>
    ) {
        dao.insert(accountId, items)
    }

}