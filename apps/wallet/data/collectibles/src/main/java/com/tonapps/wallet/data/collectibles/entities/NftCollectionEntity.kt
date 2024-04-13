package com.tonapps.wallet.data.collectibles.entities

import android.os.Parcelable
import io.tonapi.models.NftItemCollection
import kotlinx.parcelize.Parcelize

@Parcelize
data class NftCollectionEntity(
    val address: String,
    val name: String,
    val description: String,
): Parcelable {

    constructor(model: NftItemCollection) : this(
        address = model.address,
        name = model.name,
        description = model.description
    )
}