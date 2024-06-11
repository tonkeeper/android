package com.tonapps.wallet.data.account.legacy

import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletV3R1Contract
import com.tonapps.blockchain.ton.contract.WalletV3R2Contract
import com.tonapps.blockchain.ton.contract.WalletV4R1Contract
import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.toWalletAddress
import com.tonapps.wallet.data.account.WalletSource
import com.tonapps.wallet.data.account.WalletType
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell

data class WalletLegacy(
    val id: String,
    val name: String,
    val publicKey: PublicKeyEd25519,
    val type: WalletType,
    val emoji: CharSequence,
    val color: Int,
    val version: WalletVersion,
    val source: WalletSource
) {

    companion object {
        const val WORKCHAIN = 0
    }

    val key: String by lazy {
        address + "_" + version.name + "_" + type.name
    }

    val contract: BaseWalletContract by lazy {
        when (version) {
            WalletVersion.V3R2 -> WalletV3R2Contract(WORKCHAIN, publicKey)
            WalletVersion.V3R1 -> WalletV3R1Contract(WORKCHAIN, publicKey)
            WalletVersion.V4R1 -> WalletV4R1Contract(WORKCHAIN, publicKey)
            WalletVersion.V4R2 -> WalletV4R2Contract(WORKCHAIN, publicKey)
            WalletVersion.UNKNOWN -> throw IllegalArgumentException("Unknown wallet version")
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