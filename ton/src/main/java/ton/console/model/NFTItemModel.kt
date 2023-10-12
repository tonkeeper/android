package com.tonkeeper.ton.console.model

import org.json.JSONObject

data class NFTItemModel(
    val collection: NFTCollectionModel,
    val metadata: NFTMetadataModel,
    val previews: List<NFTPreviewModel>
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
        collection = NFTCollectionModel.parse(json.optJSONObject("collection")),
        metadata = NFTMetadataModel(json.getJSONObject("metadata")),
        previews = NFTPreviewModel.parse(json.getJSONArray("previews"))
    )

    fun previewByResolution(resolution: String): NFTPreviewModel? {
        return previews.firstOrNull { it.resolution == resolution }
    }
}