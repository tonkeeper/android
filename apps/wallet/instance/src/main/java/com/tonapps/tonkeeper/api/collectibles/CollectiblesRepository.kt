package com.tonapps.tonkeeper.api.collectibles

import com.tonapps.wallet.api.Tonapi
import com.tonapps.tonkeeper.api.base.AccountKey
import com.tonapps.tonkeeper.api.base.BaseAccountRepository
import com.tonapps.wallet.api.SourceAPI
import com.tonapps.tonkeeper.api.collectibles.db.CollectiblesDao
import com.tonapps.tonkeeper.api.fromJSON
import io.tonapi.apis.AccountsApi
import io.tonapi.models.NftItem

class CollectiblesRepository(
    private val api: SourceAPI<AccountsApi> = Tonapi.accounts,
    private val dao: CollectiblesDao = com.tonapps.tonkeeper.App.db.collectiblesDao()
): BaseAccountRepository<NftItem>() {

    suspend fun getNftItemCache(
        nftAddress: String,
    ): NftItem? {
        val data = dao.getItemData(nftAddress) ?: return null
        return fromJSON(data)
    }

    override suspend fun deleteCache(accountKey: AccountKey) {
        dao.delete(accountKey.toString())
    }

    override suspend fun onCacheRequest(
        accountKey: AccountKey
    ): List<NftItem> {
        return dao.get(accountKey.toString())
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
        accountId: String,
        testnet: Boolean,
    ): List<NftItem> {
        return api.get(testnet).getAccountNftItems(
            accountId = accountId,
            limit = 100,
            indirectOwnership = true,
        ).nftItems
    }

    override suspend fun insertCache(
        accountKey: AccountKey,
        items: List<NftItem>
    ) {
        dao.insert(accountKey.toString(), items)
    }

}