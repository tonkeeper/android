package com.tonapps.tonkeeper.ui.screen.collectibles.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.data.collectibles.entities.NftEntity

data class Item(
    val entity: NftEntity
): BaseListItem(0) {

    val nftAddress: String
        get() = entity.address

    val imageURI: Uri
        get() = entity.mediumUri

    val title: String
        get() = entity.name

    val collectionName: String
        get() = entity.collectionName

    val testnet: Boolean
        get() = entity.testnet

}