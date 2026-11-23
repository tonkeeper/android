package com.tonapps.signer.screen.sign.body

import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddressInt

sealed interface SignAction {

    data class Send(
        val destination: MsgAddressInt,
        val value: Coins,
        val sendMode: Int,
        val payload: SendPayload,
        val comment: String?,
    ) : SignAction {

        val sendsEntireBalance: Boolean
            get() = sendMode and SEND_MODE_CARRY_ALL_BALANCE != 0

        val destroysContract: Boolean
            get() = sendMode and SEND_MODE_DESTROY_IF_ZERO != 0
    }

    data class AddExtension(val address: AddrStd) : SignAction

    data class RemoveExtension(val address: AddrStd) : SignAction

    data class SetSignatureAuth(val allowed: Boolean) : SignAction

    data class InstallPlugin(val address: AddrStd, val amount: Coins) : SignAction

    data class RemovePlugin(val address: AddrStd, val amount: Coins) : SignAction
}

sealed interface SendPayload {

    data object Ton : SendPayload

    data class Jetton(val recipient: MsgAddressInt, val excess: MsgAddressInt) : SendPayload

    data class Nft(val newOwner: MsgAddressInt, val excess: MsgAddressInt) : SendPayload

    data class Unknown(val opCode: Long) : SendPayload
}

private const val SEND_MODE_CARRY_ALL_BALANCE = 128
private const val SEND_MODE_DESTROY_IF_ZERO = 32
