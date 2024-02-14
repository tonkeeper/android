package com.tonapps.tonkeeper.fragment.wallet.collectibles.list

import android.net.Uri
import com.tonapps.tonkeeper.api.collectionName
import com.tonapps.tonkeeper.api.imageURL
import com.tonapps.tonkeeper.api.title
import io.tonapi.models.NftItem

data class CollectiblesItem(
    val nftAddress: String,
    val imageURI: Uri,
    val title: String,
    val collectionName: String,
    val mark: Boolean
): com.tonapps.uikit.list.BaseListItem() {

    constructor(data: NftItem) : this(
        nftAddress = data.address,
        imageURI = Uri.parse(data.imageURL),
        title = data.title,
        collectionName = data.collectionName,
        mark = false
    )
}
