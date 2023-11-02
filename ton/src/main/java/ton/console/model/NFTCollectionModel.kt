package ton.console.model

import org.json.JSONObject

data class NFTCollectionModel(
    val name: String,
    val description: String?,
) {

    companion object {
        fun parse(json: JSONObject?): NFTCollectionModel {
            if (json == null) {
                return NFTCollectionModel(
                    name = "",
                    description = null
                )
            }
            return NFTCollectionModel(json)
        }
    }

    constructor(json: JSONObject) : this(
        name = json.getString("name"),
        description = json.optString("description"),
    )
}