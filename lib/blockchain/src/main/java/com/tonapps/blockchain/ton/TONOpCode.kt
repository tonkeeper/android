package com.tonapps.blockchain.ton

enum class TONOpCode(val code: Long) {
    UNKNOWN(0),
    OUT_ACTION_SEND_MSG_TAG(0x0ec3c86d),
    SIGNED_EXTERNAL(0x7369676e),
    SIGNED_INTERNAL(0x73696e74),
    JETTON_TRANSFER(0xf8a7ea5),
    NFT_TRANSFER(0x5fcc3d14),
    STONFI_SWAP(0x25938561),
    STONFI_SWAP_V2(0x6664de2a),
    CHANGE_DNS_RECORD(0x4eb1f0f9),
    LIQUID_TF_DEPOSIT(0x47d54391),
    LIQUID_TF_BURN(0x595f07bc),
    WHALES_DEPOSIT(2077040623),
    WHALES_WITHDRAW(3665837821),
    GASLESS(0x878da6e3),
    BATTERY_PAYLOAD(0xb7b2515f),
    SIGN_DATA(0x75569022),
}
