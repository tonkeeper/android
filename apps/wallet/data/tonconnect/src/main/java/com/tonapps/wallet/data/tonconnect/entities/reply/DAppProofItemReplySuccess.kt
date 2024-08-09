package com.tonapps.wallet.data.tonconnect.entities.reply

import com.tonapps.wallet.data.account.entities.ProofEntity
import kotlinx.serialization.Serializable
import org.json.JSONObject

data class DAppProofItemReplySuccess(
    val name: String = "ton_proof",
    val proof: ProofEntity
): DAppReply() {

    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("name", name)
        json.put("proof", proof.toJSON(true))
        return json
    }

}