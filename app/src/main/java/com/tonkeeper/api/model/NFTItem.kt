package com.tonkeeper.api.model

import org.json.JSONObject

data class NFTItem(
    val collection: NFTCollection,
    val metadata: NFTMetadata,
    val previews: List<NFTPreview>
) {

    val displayTitle: String by lazy {
        metadata.name ?: collection.name
    }

    val displayDescription: String by lazy {
        collection.description ?: collection.name
    }

    val displayImageURL: String by lazy {
        var preview = previewByResolution("250x250")
        if (preview == null) {
            preview = previewByResolution("500x500")
        }
        if (preview == null) {
            preview = previewByResolution("100x100")
        }
        if (preview == null) {
            preview = previews.first()
        }
        preview.url
    }

    constructor(json: JSONObject) : this(
        collection = NFTCollection.parse(json.optJSONObject("collection")),
        metadata = NFTMetadata(json.getJSONObject("metadata")),
        previews = NFTPreview.parse(json.getJSONArray("previews"))
    )

    fun previewByResolution(resolution: String): NFTPreview? {
        return previews.firstOrNull { it.resolution == resolution }
    }
}