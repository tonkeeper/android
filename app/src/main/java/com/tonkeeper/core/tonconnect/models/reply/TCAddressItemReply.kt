package com.tonkeeper.core.tonconnect.models.reply

import kotlinx.serialization.Serializable

@Serializable
class TCAddressItemReply(
    val name: String = "ton_addr",
    val address: String,
    val network: String = "mainnet",
    val walletStateInit: String,
    val publicKey: String
): TCReply()