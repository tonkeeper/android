package com.tonkeeper.core_ton.data

import org.ton.block.AddrStd

data class TonAccount(
    val mnemonic: List<String>,
    val address: AddrStd,
    val privateKey: ByteArray,
    val publicKey: ByteArray,
    val sharedKey: ByteArray
) {
}
