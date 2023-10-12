package com.tonkeeper.ton.console.method

import com.tonkeeper.ton.console.model.JettonItemModel
import org.json.JSONObject

class JettonsMethod(
    address: String
): BaseMethod<List<JettonItemModel>>("accounts/$address/jettons") {

    override fun parseJSON(response: JSONObject): List<JettonItemModel> {
        val array = response.getJSONArray("balances")
        val list = mutableListOf<JettonItemModel>()
        for (i in 0 until array.length()) {
            list.add(JettonItemModel(array.getJSONObject(i)))
        }
        return list
    }
}
