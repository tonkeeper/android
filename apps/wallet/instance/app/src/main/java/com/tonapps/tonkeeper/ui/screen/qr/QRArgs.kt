package com.tonapps.tonkeeper.ui.screen.qr

import android.os.Bundle
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.getEnum
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.putEnum
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.Wallet
import uikit.base.BaseArgs

data class QRArgs(
    val address: String,
    val token: TokenEntity,
    val walletType: Wallet.Type
): BaseArgs() {

    private companion object {
        private const val ARG_ADDRESS = "address"
        private const val ARG_TOKEN = "token"
        private const val ARG_WALLET_TYPE = "wallet_type"
    }

    constructor(bundle: Bundle) : this(
        address = bundle.getString(ARG_ADDRESS)!!,
        token = bundle.getParcelableCompat(ARG_TOKEN)!!,
        walletType = bundle.getEnum<Wallet.Type>(ARG_WALLET_TYPE, Wallet.Type.Default)
    )

    fun getDeepLink(): String {
        var deepLink = "ton://transfer/${address}"
        if (!token.isTon) {
            deepLink += "?token=${token.address.toUserFriendly(
                wallet = false, 
                testnet = walletType == Wallet.Type.Testnet
            )}"
        }
        return deepLink
    }

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString(ARG_ADDRESS, address)
        bundle.putParcelable(ARG_TOKEN, token)
        bundle.putEnum(ARG_WALLET_TYPE, walletType)
        return bundle
    }
}