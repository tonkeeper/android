package com.tonapps.tonkeeper.ui.screen.init

import android.os.Bundle
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.publicKey
import com.tonapps.extensions.getEnum
import com.tonapps.extensions.putEnum
import com.tonapps.wallet.data.account.WalletSource
import org.ton.api.pub.PublicKeyEd25519
import uikit.base.BaseArgs

data class InitArgs(
    val type: Type,
    val name: String?,
    val publicKeyEd25519: PublicKeyEd25519?,
    val walletSource: WalletSource?
): BaseArgs() {

    enum class Type {
        New, Import, Watch, Testnet, Signer
    }

    private companion object {
        private const val ARG_TYPE = "type"
        private const val ARG_PUBLIC_KEY = "pk"
        private const val ARG_NAME = "name"
        private const val ARG_WALLET_SOURCE = "wallet_source"
    }

    constructor(bundle: Bundle) : this(
        type = bundle.getEnum(ARG_TYPE, Type.New),
        name = bundle.getString(ARG_NAME),
        publicKeyEd25519 = bundle.getString(ARG_PUBLIC_KEY)?.publicKey(),
        walletSource = bundle.getEnum(ARG_WALLET_SOURCE, WalletSource.Default)
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putEnum(ARG_TYPE, type)
        name?.let { putString(ARG_NAME, it) }
        publicKeyEd25519?.let { putString(ARG_PUBLIC_KEY, it.base64()) }
        walletSource?.let { putEnum(ARG_WALLET_SOURCE, it) }
    }
}