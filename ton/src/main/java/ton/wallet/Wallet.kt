package ton.wallet

import android.os.Parcel
import android.os.Parcelable
import org.ton.api.pub.PublicKeyEd25519
import org.ton.api.tonnode.Workchain
import org.ton.block.AddrStd
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.contract.wallet.WalletContract
import ton.contract.WalletV4R2Contract

data class Wallet(
    val id: Long,
    val name: String?,
    val publicKey: PublicKeyEd25519,
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
        )
    }

    val address: String by lazy {
        AddrStd(accountId).toString(userFriendly = true)
    }

    constructor(legacy: WalletInfo) : this(
        id = legacy.createDate,
        name = legacy.name,
        publicKey = legacy.publicKey
    )

    fun isMyAddress(a: String): Boolean {
        return address == a || a == accountId
    }
}