package ton.wallet

import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.contract.wallet.WalletContract
import ton.contract.WalletV4R2Contract
import ton.extensions.toUserFriendly
import ton.extensions.toWalletAddress

data class Wallet(
    val id: Long,
    val name: String?,
    val publicKey: PublicKeyEd25519,
    val type: WalletType,
) {

    companion object {
        const val WORKCHAIN = 0
    }

    val contract: WalletV4R2Contract by lazy {
        WalletV4R2Contract(WORKCHAIN, publicKey)
    }

    val stateInit: StateInit by lazy {
        WalletV4R2Contract.createStateInit(publicKey, WalletContract.DEFAULT_WALLET_ID + WORKCHAIN)
    }

    val accountId: String by lazy {
        MsgAddressInt.toString(
            contract.address,
            userFriendly = false
        ).lowercase()
    }

    val address: String by lazy {
        AddrStd(accountId).toWalletAddress()
    }

    val hasPrivateKey: Boolean
        get() = type == WalletType.Default || type == WalletType.Testnet

    val testnet: Boolean
        get() = type == WalletType.Testnet

    fun isMyAddress(a: String): Boolean {
        return address.equals(a, ignoreCase = true) || accountId.equals(a, ignoreCase = true)
    }
}