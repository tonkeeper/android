package com.tonapps.tonkeeper.ui.screen.send.main.state

import com.tonapps.icu.Coins
import org.ton.block.AddrStd

sealed class SendTransferType {

    interface WithExcessesAddress {
        val excessesAddress: AddrStd
    }

    data object Default : SendTransferType()

    data class Battery(
        override val excessesAddress: AddrStd
    ): SendTransferType(), WithExcessesAddress

    data class Gasless(
        override val excessesAddress: AddrStd,
        val gaslessFee: Coins,
    ): SendTransferType(), WithExcessesAddress
}