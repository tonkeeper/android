package ton.console.model

import org.json.JSONObject

data class EncryptedCommentModel(
    val encryptionType: String,
    val cipherText: String
) {

    companion object {
        fun parse(json: JSONObject?): EncryptedCommentModel? {
            if (json == null) return null
            return EncryptedCommentModel(json)
        }
    }

    constructor(json: JSONObject) : this(
        encryptionType = json.getString("encryption_type"),
        cipherText = json.getString("cipher_text")
    )
}