package com.tonapps.tonkeeper.ui.screen.events.compose.history.state

import com.tonapps.wallet.api.entity.value.BlockchainAddress

data class TxTronParams(
    val address: BlockchainAddress? = null,
    val tonProofToken: String? = null
) {

    val isEmtpy: Boolean
        get() = address == null && tonProofToken == null
}