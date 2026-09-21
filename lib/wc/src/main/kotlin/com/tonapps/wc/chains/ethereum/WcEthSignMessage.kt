package com.tonapps.wc.chains.ethereum

import com.tonapps.wc.chains.WcRequestData

data class WcEthSignMessage(
    val raw: List<String>,
    val type: WcSignType,
) : WcRequestData {

    enum class WcSignType {
        Default,
        Message,
        PersonalMessage,
        TypedMessage,
        Transaction,
    }

    /**
     * Raw parameters will always be the message and the addess. Depending on the WcSignType,
     * those parameters can be swapped as description below:
     *
     *  - MESSAGE: `[address, data ]`
     *  - TYPED_MESSAGE: `[address, data]`
     *  - PERSONAL_MESSAGE: `[data, address]`
     *
     *  reference: https://docs.walletconnect.org/json-rpc/ethereum#eth_signtypeddata
     */
    val data get() = when (type) {
        WcSignType.Message -> raw[1]
        WcSignType.TypedMessage -> raw[1]
        else -> raw[0]
    }

    val address
        get() = when (type) {
            WcSignType.Message -> raw[0]
            WcSignType.TypedMessage -> raw[0]
            else -> raw[1]
        }
}
