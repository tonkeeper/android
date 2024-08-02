package com.tonapps.blockchain.ton.contract.w5

enum class W5OpCodes(val code: Long) {
    AuthExtension(0x6578746e),
    AuthSignedExternal(0x7369676e),
    AuthSignedInternal(0x73696e74)
}