package com.tonkeeper.core.tonconnect.models.reply

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
        json.put("network", network)
        json.put("wallet_state_init", walletStateInit)
        json.put("public_key", publicKey)
        return json
    }

}