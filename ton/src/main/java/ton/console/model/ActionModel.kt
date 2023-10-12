package com.tonkeeper.ton.console.model

import org.json.JSONArray
import org.json.JSONObject

data class ActionModel(
    val type: String,
    val status: String,
    val tonTransfer: TonTransferModel? = null,
    val simplePreview: EventSimplePreviewModel
) {

    companion object {
        fun parse(array: JSONArray): List<ActionModel> {
            val result = mutableListOf<ActionModel>()
            for (i in 0 until array.length()) {
                result.add(ActionModel(array.getJSONObject(i)))
            }
            return result
        }
    }

    constructor(json: JSONObject) : this(
        json.getString("type"),
        json.getString("status"),
        TonTransferModel.parse(json.optJSONObject("ton_transfer")),
        EventSimplePreviewModel(json.getJSONObject("simple_preview"))
    )
}