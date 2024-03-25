package com.tonapps.wallet.data.collectibles.entities

import android.os.Parcelable
import android.util.Log
import io.tonapi.models.ImagePreview
import kotlinx.parcelize.Parcelize

@Parcelize
data class NftPreviewEntity(
    val width: Int,
    val height: Int,
    val url: String
): Parcelable {

    constructor(model: ImagePreview) : this(
        resolution = parseResolution(model.resolution),
        url = model.url
    )

    constructor(resolution: Pair<Int, Int>, url: String) : this(
        width = resolution.first,
        height = resolution.second,
        url = url
    )
    private companion object {

        fun parseResolution(resolution: String): Pair<Int, Int> {
            val parts = resolution.split('x')
            return parts[0].toInt() to parts[1].toInt()
        }
    }
}