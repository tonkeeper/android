package ton.console.model

import org.json.JSONObject

data class AccountEventModel(
    val eventId: String,
    val account: AccountAddressModel,
    val description: String?,
    val timestamp: Long,
    val scam: Boolean,
    val inProgress: Boolean,
    val actions: List<ActionModel>
) {

    constructor(json: JSONObject) : this(
        json.getString("event_id"),
        AccountAddressModel(json.getJSONObject("account")),
        json.optString("description"),
        json.getLong("timestamp"),
        json.optBoolean("is_scam"),
        json.optBoolean("in_progress"),
        ActionModel.parse(json.getJSONArray("actions"))
    )
}