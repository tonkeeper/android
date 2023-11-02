package ton.console.method

import ton.console.model.AccountModel
import org.json.JSONObject

class AccountMethod(
    address: String
): BaseMethod<AccountModel>("accounts/$address") {

    override fun parseJSON(response: JSONObject) = AccountModel(response)
}