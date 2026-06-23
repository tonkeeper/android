package com.tonapps.deposit.screens.send.contact

data class SendContactResult(
    val type: Int,
    val address: String,
) {
    companion object {
        const val MY_WALLET_TYPE = 1
        const val CONTACT_TYPE = 2
    }
}
