package com.tonapps.tonkeeper.ui.screen.external.qr.signer.sign

import android.os.Bundle
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.contract.walletVersion
import com.tonapps.blockchain.ton.extensions.cellFromHex
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.cell.Cell
import uikit.base.BaseArgs

data class SignerSignArgs(
    val publicKey: PublicKeyEd25519,
    val unsignedBody: Cell,
    val walletVersion: WalletVersion,
    val network: TonNetwork,
    val label: String
): BaseArgs() {

    constructor(bundle: Bundle) : this(
        publicKey = bundle.getString(ARG_PK)!!.publicKeyFromHex(),
        unsignedBody = bundle.getString(ARG_BODY)!!.cellFromHex(),
        walletVersion = walletVersion(bundle.getInt(ARG_WALLET_VERSION, WalletVersion.UNKNOWN.id)),
        network = requireNotNull(TonNetwork.from(bundle.getInt(ARG_NETWORK))),
        label = bundle.getString(ARG_LABEL)!!
    )

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString(ARG_PK, publicKey.hex())
        bundle.putString(ARG_BODY, unsignedBody.hex())
        bundle.putInt(ARG_WALLET_VERSION, walletVersion.id)
        bundle.putInt(ARG_NETWORK, network.value)
        bundle.putString(ARG_LABEL, label)
        return bundle
    }

    private companion object {
        private const val ARG_PK = "pk"
        private const val ARG_BODY = "body"
        private const val ARG_WALLET_VERSION = "wallet_version"
        private const val ARG_NETWORK = "network"
        private const val ARG_LABEL = "label"
    }
}