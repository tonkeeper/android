package ton.wallet

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.WalletContract
import org.ton.mnemonic.Mnemonic
import ton.contract.WalletV4R2Contract

data class WalletInfo(
    val createDate: Long,
    val name: String,
    val words: List<String>,
    private val seed: ByteArray,
) {

    val workchain = 0
    val walletId: Int = WalletContract.DEFAULT_WALLET_ID + workchain

    constructor(
        createDate: Long,
        name: String,
        words: List<String>
    ) : this(
        createDate = createDate,
        name = name,
        words = words,
        seed = Mnemonic.toSeed(words)
    )

    val privateKey: PrivateKeyEd25519 by lazy {
        PrivateKeyEd25519(seed)
    }

    val publicKey: PublicKeyEd25519 by lazy {
        privateKey.publicKey()
    }

    val contract: WalletV4R2Contract by lazy {
        WalletV4R2Contract(0, publicKey)
    }

    val stateInit: StateInit by lazy {
        val data = CellBuilder.createCell {
            storeUInt(0, 32)
            storeUInt(walletId, 32)
            storeBits(publicKey.key)
            storeBit(false)
        }
        StateInit(
            code = WalletV4R2Contract.CODE,
            data = data
        )
    }

    val accountId: String by lazy {
        MsgAddressInt.toString(
            contract.address,
            userFriendly = false
        )
    }

    val address: String by lazy {
        AddrStd(accountId).toString(userFriendly = true)
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