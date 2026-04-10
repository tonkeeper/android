package com.tonapps.blockchain.model.account

data class Account(
    val address: String,
    val publicKey: PubKey,
    val derivation: Derivation,
    val asset: Asset,
    val networkMode: NetworkMode = NetworkMode.Mainnet,
)
