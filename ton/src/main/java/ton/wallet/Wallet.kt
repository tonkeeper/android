package ton.wallet

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import ton.contract.BaseWalletContract
import ton.contract.WalletV3R1Contract
import ton.contract.WalletV3R2Contract
import ton.contract.WalletV4R2Contract
import ton.contract.WalletVersion
import ton.extensions.toWalletAddress

data class Wallet(
    val id: Long,
    val name: String,
    val publicKey: PublicKeyEd25519,
    val type: WalletType,
    val emoji: CharSequence,
    val color: Int,
    val version: WalletVersion = WalletVersion.V4R2
) {

    companion object {
        const val WORKCHAIN = 0
    }

    fun asVersion(version: WalletVersion): Wallet {
        return copy(version = version)
    }

    val contract: BaseWalletContract by lazy {
        when (version) {
            WalletVersion.V4R2 -> WalletV4R2Contract(WORKCHAIN, publicKey)
            WalletVersion.V3R2 -> WalletV3R2Contract(WORKCHAIN, publicKey)
            WalletVersion.V3R1 -> WalletV3R1Contract(WORKCHAIN, publicKey)
        }
    }

    val stateInit: StateInit
        get() = contract.stateInit

    val accountId: String by lazy {
        MsgAddressInt.toString(
            contract.address,
            userFriendly = false
        ).lowercase()
    }

    val address: String by lazy {
        AddrStd(accountId).toWalletAddress(testnet)
    }

    val hasPrivateKey: Boolean
        get() = type == WalletType.Default || type == WalletType.Testnet

    val testnet: Boolean
        get() = type == WalletType.Testnet

    val signer: Boolean
        get() = type == WalletType.Signer

    fun isMyAddress(a: String): Boolean {
        return address.equals(a, ignoreCase = true) || accountId.equals(a, ignoreCase = true)
    }

    fun sign(
        privateKey: PrivateKeyEd25519,
        seqno: Int,
        unsignedBody: Cell
    ): Cell {
        return contract.createTransferMessageCell(
            address = contract.address,
            privateKey = privateKey,
            seqno = seqno,
            unsignedBody = unsignedBody,
        )
    }

}