package com.tonkeeper.api.method

import com.tonkeeper.api.model.NFTItem
import org.json.JSONObject

class NftsMethod(
    address: String
): BaseMethod<List<NFTItem>>("accounts/$address/nfts") {

    init {
        querySet("limit", 1000)
    }

    override fun parseJSON(response: JSONObject): List<NFTItem> {
        val array = response.getJSONArray("nft_items")
        val result = mutableListOf<NFTItem>()
        for (i in 0 until array.length()) {
            result.add(NFTItem(array.getJSONObject(i)))
        }
        return result
    }

}