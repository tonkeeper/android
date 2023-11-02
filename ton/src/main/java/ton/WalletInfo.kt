package ton

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.MsgAddressInt
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

    val publicKey: PublicKeyEd25519 by lazy {
        privateKey.publicKey()
    }

    val contract: WalletV4R2Contract by lazy {
        WalletV4R2Contract(0, publicKey)
    }

    val address: String by lazy {
        if (false) {
            "EQD2NmD_lH5f5u1Kj3KfGyTvhZSX0Eg6qp2a5IQUKXxOG21n"
        } else {
            MsgAddressInt.toString(contract.address)
        }
    }

    fun isMyAddress(address: String): Boolean {
        return try {
            AddrStd.parse(this.address) == AddrStd.parse(address)
        } catch (e: Throwable) {
            false
        }
    }

    override fun toString(): String {
        return "WalletInfo(address='$address')"
    }
}