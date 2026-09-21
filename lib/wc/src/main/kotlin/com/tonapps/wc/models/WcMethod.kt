package com.tonapps.wc.models

import kotlinx.serialization.SerialName

enum class WcMethod(
    val methodName: String
) {
    // TRON
    @SerialName("tron_signMessage")
    TRON_SIGN_MESSAGE("tron_signMessage"),

    @SerialName("tron_signTransaction")
    TRON_SIGN_TRANSACTION("tron_signTransaction"),

    // ETH
    @SerialName("personal_sign")
    ETH_PERSONAL_SIGN("personal_sign"),

    @SerialName("eth_signTypedData_v4")
    ETH_SIGN_TYPE_DATA_V4("eth_signTypedData_v4"),

    @SerialName("eth_signTransaction")
    ETH_SIGN_TRANSACTION( "eth_signTransaction"),

    @SerialName("eth_sendTransaction")
    ETH_SEND_TRANSACTION( "eth_sendTransaction"),

    @SerialName("wallet_getCapabilities")
    WALLET_GET_CAPABILITIES("wallet_getCapabilities"),

    @SerialName("wallet_watchAsset")
    WALLET_WATCH_ASSET("wallet_watchAsset"),
    ;

    companion object {
        fun findByName(method: String): WcMethod? {
            return values()
                .firstOrNull { it.methodName == method }
        }
    }
}
