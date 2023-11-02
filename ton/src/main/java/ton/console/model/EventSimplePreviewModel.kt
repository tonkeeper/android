package ton.console.model

import org.json.JSONObject

data class EventSimplePreviewModel(
    val name: String,
    val description: String,
    val value: String?,
    val accounts: List<AccountAddressModel>
) {

    constructor(jsonObject: JSONObject) : this(
        jsonObject.getString("name"),
        jsonObject.getString("description"),
        jsonObject.optString("value"),
        AccountAddressModel.parse(jsonObject.getJSONArray("accounts"))
    )
}