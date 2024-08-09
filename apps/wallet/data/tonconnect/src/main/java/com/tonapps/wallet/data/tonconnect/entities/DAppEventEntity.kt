package com.tonapps.wallet.data.tonconnect.entities

import com.tonapps.wallet.data.account.entities.WalletEntity
import org.json.JSONArray
import org.json.JSONObject

data class DAppEventEntity(
    val wallet: WalletEntity,
    val connect: DConnectEntity,
    val json: JSONObject
) {

    val method: String
        get() = json.getString("method")

    val params: JSONArray
        get() = json.getJSONArray("params")

    val id: String
        get() = json.getString("id")

    constructor(
        wallet: WalletEntity,
        connect: DConnectEntity,
        body: ByteArray
    ) : this(
        wallet = wallet,
        connect = connect,
        json = JSONObject(connect.decrypt(body).toString(Charsets.UTF_8))
    )

    companion object {
        fun parseParam(param: Any): JSONObject {
            if (param is String) {
                return JSONObject(param)
            } else if (param is JSONObject) {
                return param
            }
            throw IllegalArgumentException("Invalid param type")
        }
    }
}