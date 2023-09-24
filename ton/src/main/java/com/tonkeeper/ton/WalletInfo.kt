package com.tonkeeper.ton

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.MsgAddressInt
import org.ton.contract.wallet.WalletContract
import org.ton.contract.wallet.WalletV4R2Contract
import org.ton.mnemonic.Mnemonic

class WalletInfo(
    val words: List<String>,
    private val seed: ByteArray,
) {

    constructor(words: List<String>) : this(words, Mnemonic.toSeed(words))

    val privateKey: PrivateKeyEd25519 by lazy {
        PrivateKeyEd25519(seed)
    }

    val contract: WalletV4R2Contract by lazy {
        WalletV4R2Contract(WalletContract.DEFAULT_WALLET_ID, privateKey.publicKey())
    }

    val address: String by lazy {
        MsgAddressInt.toString(contract.address)
    }

    override fun toString(): String {
        return "WalletInfo(address='$address')"
    }
}