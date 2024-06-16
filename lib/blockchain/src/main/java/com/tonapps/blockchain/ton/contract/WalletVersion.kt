package com.tonapps.blockchain.ton.contract

enum class WalletVersion(val id: Int, val title: String, val index: Int) {
    V5R1(4, "v5R1", 0),
    V4R2(0, "v4R2", 4),
    V4R1(3, "v4R1", 3),
    V3R2(1, "v3R2", 2),
    V3R1(2, "v3R1", 1),
    UNKNOWN(-1, "unknown", 0)
}

fun walletVersion(id: Int): WalletVersion {
    return WalletVersion.entries.find { it.id == id } ?: WalletVersion.UNKNOWN
}

fun walletVersion(title: String): WalletVersion {
    return WalletVersion.entries.find { it.title == title } ?: WalletVersion.UNKNOWN
}