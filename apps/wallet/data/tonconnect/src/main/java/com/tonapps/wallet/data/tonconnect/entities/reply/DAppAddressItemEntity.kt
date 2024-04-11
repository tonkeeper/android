package com.tonapps.wallet.data.tonconnect.entities.reply

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.bocBase64
import com.tonapps.blockchain.ton.extensions.hex
import org.json.JSONObject
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.StateInit

data class DAppAddressItemEntity(
    val name: String = "ton_addr",
    val address: String,
    val network: TonNetwork,
    val walletStateInit: StateInit,
    val publicKey: PublicKeyEd25519
): DAppReply() {

    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("name", name)
        json.put("address", address)
        json.put("network", network.value.toString())
        json.put("walletStateInit", walletStateInit.bocBase64())
        json.put("publicKey", publicKey.hex())
        return json
    }

}