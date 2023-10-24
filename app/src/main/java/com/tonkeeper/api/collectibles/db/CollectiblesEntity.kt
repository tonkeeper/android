package com.tonkeeper.api.nft.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tonkeeper.api.toJSON
import io.tonapi.models.NftItem

@Entity(tableName = "nft")
data class NftEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val data: String
) {

    companion object {

        private fun createId(accountId: String, address: String): String {
            return "$accountId-$address"
        }

        fun map(accountId: String, list: List<NftItem>): List<NftEntity> {
            return list.map { NftEntity(
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
        accountId = accountId,
        data = toJSON(nft)
    )

}