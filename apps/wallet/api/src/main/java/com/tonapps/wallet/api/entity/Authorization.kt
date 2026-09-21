package com.tonapps.wallet.api.entity

data class Authorization(
    val tonProof: String? = null,
    val deviceToken: String? = null,
    val walletId: String? = null,
) {

    val isEmpty: Boolean
        get() = tonProof.isNullOrBlank() && deviceToken.isNullOrBlank()

    companion object {
        val Empty = Authorization()
    }
}
