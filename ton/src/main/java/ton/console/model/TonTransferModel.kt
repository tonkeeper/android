package ton.console.model

import org.json.JSONObject

data class TonTransferModel(
    val sender: AccountAddressModel,
    val recipient: AccountAddressModel,
    val amount: Long,
    val comment: String?,
    val encryptedComment: EncryptedCommentModel?,
    val refund: ReFoundModel?
) {

    companion object {
        fun parse(json: JSONObject?): TonTransferModel? {
            if (json == null) return null
            return TonTransferModel(json)
        }
    }

    constructor(json: JSONObject) : this(
        sender = AccountAddressModel(json.getJSONObject("sender")),
        recipient = AccountAddressModel(json.getJSONObject("recipient")),
        amount = json.getLong("amount"),
        comment = json.optString("comment"),
        encryptedComment = EncryptedCommentModel.parse(json.optJSONObject("encryptedComment")),
        refund = ReFoundModel.parse(json.optJSONObject("refund"))
    )
}