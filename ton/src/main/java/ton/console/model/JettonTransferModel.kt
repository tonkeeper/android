package com.tonkeeper.ton.console.model

import org.json.JSONObject

data class JettonTransferModel(
    val sender: AccountAddressModel?,
    val receiver: AccountAddressModel?,
    val sendersWallet: String,
    val recipientsWallet: String,
    val amount: String,
    val comment: String?,
    val encryptedComment: EncryptedCommentModel?,
    val refund: ReFoundModel?,
    val jetton: JettonPreviewModel
) {

    constructor(json: JSONObject) : this(
        sender = AccountAddressModel.parse(json.optJSONObject("sender")),
        receiver = AccountAddressModel.parse(json.optJSONObject("receiver")),
        sendersWallet = json.optString("sendersWallet"),
        recipientsWallet = json.optString("recipientsWallet"),
        amount = json.optString("amount"),
        comment = json.optString("comment"),
        encryptedComment = EncryptedCommentModel.parse(json.optJSONObject("encryptedComment")),
        refund = ReFoundModel.parse(json.optJSONObject("refund")),
        jetton = JettonPreviewModel(json.getJSONObject("jetton"))
    )
}