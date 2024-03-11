package com.tonapps.tonkeeper.fragment.wallet.collectibles.list

import android.net.Uri
import com.tonapps.tonkeeper.api.collectionName
import com.tonapps.tonkeeper.api.imageURL
import com.tonapps.tonkeeper.api.title
import com.tonapps.uikit.list.BaseListItem
import io.tonapi.models.NftItem

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_NFT = 0
        const val TYPE_SKELETON = 4
    }

    data class Nft(
        val nftAddress: String,
        val imageURI: Uri,
        val title: String,
        val collectionName: String,
        val mark: Boolean
    ): Item(TYPE_NFT) {

        constructor(data: NftItem) : this(
            nftAddress = data.address,
            imageURI = Uri.parse(data.imageURL),
            title = data.title,
            collectionName = data.collectionName,
            mark = false
        )
    }

    data object Skeleton: Item(TYPE_SKELETON) {

        val list = List(24) { Skeleton }
    }

}
