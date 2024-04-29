package com.tonapps.wallet.data.collectibles.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NftMetadataEntity(
    val strings: HashMap<String, String>,
): Parcelable {

    val name: String?
        get() = strings["name"]

    val description: String?
        get() = strings["description"]

    constructor(map: Map<String, Any>) : this(
        strings = map.filter { it.value is String }.mapValues { it.value as String } as HashMap<String, String>
    )
}