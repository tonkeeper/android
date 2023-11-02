package com.tonkeeper.api.collectibles.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tonkeeper.api.toJSON
import io.tonapi.models.NftItem

@Entity(tableName = "collectibles")
data class CollectiblesEntity(
    @PrimaryKey val id: String,
    val nftAddress: String,
    val accountId: String,
    val data: String
) {

    companion object {

        private fun createId(accountId: String, address: String): String {
            return "$accountId-$address"
        }

        fun map(accountId: String, list: List<NftItem>): List<CollectiblesEntity> {
            return list.map { CollectiblesEntity(
                accountId = accountId,
                nft = it
            ) }
        }
    }

    constructor(
        accountId: String,
        nft: NftItem
    ) : this(
        id = createId(accountId, nft.address),
        nftAddress = nft.address,
        accountId = accountId,
        data = toJSON(nft)
    )

}