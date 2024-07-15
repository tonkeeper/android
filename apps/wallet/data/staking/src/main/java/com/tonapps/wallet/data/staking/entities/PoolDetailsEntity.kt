package com.tonapps.wallet.data.staking.entities

import android.os.Parcelable
import io.tonapi.models.PoolImplementation
import kotlinx.parcelize.Parcelize

@Parcelize
data class PoolDetailsEntity(
    val name: String,
    val description: String,
    val url: String,
    val socials: List<String>,
): Parcelable {

    constructor(model: PoolImplementation) : this(
        name = model.name,
        description = model.description,
        url = model.url,
        socials = model.socials
    )
}