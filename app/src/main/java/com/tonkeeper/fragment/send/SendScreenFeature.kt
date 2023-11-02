package com.tonkeeper.fragment.send

import uikit.mvi.UiFeature

class SendScreenFeature: UiFeature<SendScreenState, SendScreenEffect>(SendScreenState()) {

    var recipient: Recipient? = null
    var amount: Amount? = null

    data class Amount(val amount: Float)
    data class Recipient(val address: String, val comment: String)
}