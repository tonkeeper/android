package ton.console.model

import ton.extensions.getStringsList
import org.json.JSONObject

data class ContractDeployModel(
    val address: String,
    val interfaces: List<String>,
) {

    constructor(json: JSONObject) : this(
        address = json.getString("address"),
        interfaces = json.getStringsList("interfaces")
    )
}