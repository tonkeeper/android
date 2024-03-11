package com.tonapps.tonkeeper.core.tonconnect.models.reply

import org.json.JSONObject

class TCAddressItemReply(
    val name: String = "ton_addr",
    val address: String,
    val network: String,
    val walletStateInit: String,
    val publicKey: String
): TCReply() {
    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("name", name)
        json.put("address", address)
        json.put("com/tonapps/network", network)
        json.put("walletStateInit", walletStateInit)
        json.put("publicKey", publicKey)
        return json
    }

}