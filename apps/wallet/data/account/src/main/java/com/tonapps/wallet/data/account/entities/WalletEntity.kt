package com.tonapps.wallet.data.account.entities

import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletV3R1Contract
import com.tonapps.blockchain.ton.contract.WalletV3R2Contract
import com.tonapps.blockchain.ton.contract.WalletV4R1Contract
import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.toWalletAddress
import com.tonapps.wallet.data.account.WalletType
import com.tonapps.wallet.data.account.legacy.WalletLegacy
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.MsgAddressInt

data class WalletEntity(
    val id: Long,
    val publicKey: PublicKeyEd25519,
    val type: WalletType,
    val version: WalletVersion = WalletVersion.V4R2,
    val label: WalletLabel,
) {

    companion object {
        const val WORKCHAIN = 0
    }

    constructor(legacy: WalletLegacy) : this(
        id = legacy.id,
        publicKey = legacy.publicKey,
        type = legacy.type,
        version = legacy.version,
        label = WalletLabel(
            name = legacy.name,
            emoji = legacy.emoji.toString(),
            color = legacy.color
        ),
    )

    val contract: BaseWalletContract = when (version) {
        WalletVersion.V4R2 -> WalletV4R2Contract(WORKCHAIN, publicKey)
        WalletVersion.V3R2 -> WalletV3R2Contract(WORKCHAIN, publicKey)
        WalletVersion.V3R1 -> WalletV3R1Contract(WORKCHAIN, publicKey)
        WalletVersion.V4R1 -> WalletV4R1Contract(WORKCHAIN, publicKey)
        else -> throw IllegalArgumentException("Unsupported wallet version: $version")
    }

    val testnet: Boolean
        get() = type == WalletType.Testnet

    val accountId: String = MsgAddressInt.toString(
        contract.address,
        userFriendly = false
    ).lowercase()

    val address: String = AddrStd(accountId).toWalletAddress(testnet)
}