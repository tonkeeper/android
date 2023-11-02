package ton.console.model

import org.json.JSONArray
import org.json.JSONObject

data class NFTPreviewModel(
    val resolution: String,
    val url: String
) {

    companion object {

        fun parse(array: JSONArray): List<NFTPreviewModel> {
            val list = mutableListOf<NFTPreviewModel>()
            for (i in 0 until array.length()) {
                list.add(NFTPreviewModel(array.getJSONObject(i)))
            }
            return list
        }
    }

    constructor(json: JSONObject) : this(
        resolution = json.getString("resolution"),
        url = json.getString("url")
    )
}