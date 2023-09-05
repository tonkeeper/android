package com.tonkeeper.api.method

import com.tonkeeper.api.model.AccountModel
import org.json.JSONObject

class AccountMethod(
    address: String
): BaseMethod<AccountModel>("accounts/$address") {

    override fun parseJSON(response: JSONObject): AccountModel {
        return AccountModel(response)
    }
}