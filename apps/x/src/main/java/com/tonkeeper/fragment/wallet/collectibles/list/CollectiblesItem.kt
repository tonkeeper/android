package com.tonkeeper.fragment.wallet.collectibles.list

import android.net.Uri
import com.tonkeeper.api.collectionName
import com.tonkeeper.api.imageURL
import com.tonkeeper.api.title
import io.tonapi.models.NftItem
import uikit.list.BaseListItem

data class CollectiblesItem(
    val nftAddress: String,
    val imageURI: Uri,
    val title: String,
    val collectionName: String,
    val mark: Boolean
): BaseListItem() {

    constructor(data: NftItem) : this(
        nftAddress = data.address,
        imageURI = Uri.parse(data.imageURL),
        title = data.title,
        collectionName = data.collectionName,
        mark = false
    )
}
