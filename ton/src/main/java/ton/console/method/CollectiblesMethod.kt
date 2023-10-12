package com.tonkeeper.ton.console.method

import com.tonkeeper.ton.console.model.NFTItemModel
import org.json.JSONObject

class CollectiblesMethod(
    address: String
): BaseMethod<List<NFTItemModel>>("accounts/$address/nfts") {

    init {
        querySet("limit", 1000)
    }

    override fun parseJSON(response: JSONObject): List<NFTItemModel> {
        val array = response.getJSONArray("nft_items")
        val result = mutableListOf<NFTItemModel>()
        for (i in 0 until array.length()) {
            result.add(NFTItemModel(array.getJSONObject(i)))
        }
        return result
    }

}