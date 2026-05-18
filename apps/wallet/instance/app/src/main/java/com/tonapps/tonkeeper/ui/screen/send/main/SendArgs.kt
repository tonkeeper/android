package com.tonapps.tonkeeper.ui.screen.send.main

import android.os.Bundle
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.bus.generated.Events
import com.tonapps.extensions.getEnum
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.putEnum
import com.tonapps.icu.Coins
import org.ton.cell.Cell
import uikit.base.BaseArgs

data class SendArgs(
    val targetAddress: String?,
    val tokenAddress: String?,
    val amount: Coins?,
    val text: String?,
    val nftAddress: String,
    val type: SendScreen.Companion.Type,
    val bin: Cell? = null,
    val from: Events.SendNative.SendNativeFrom = Events.SendNative.SendNativeFrom.WalletScreen
): BaseArgs() {

    private companion object {
        private const val ARG_TARGET_ADDRESS = "target_address"
        private const val ARG_TOKEN_ADDRESS = "token_address"
        private const val ARG_AMOUNT_NANO = "amount_nano"
        private const val ARG_TEXT = "text"
        private const val ARG_NFT_ADDRESS = "nft_address"
        private const val ARG_TYPE = "type"
        private const val ARG_BIN = "bin"
        private const val ARG_FROM = "from"

        private fun normalizeAmount(amount: Coins?): Coins? {
            if (amount?.isPositive == true) {
                return amount
            }
            return null
        }
    }

    val isNft: Boolean
        get() = nftAddress.isNotEmpty()

    constructor(bundle: Bundle) : this(
        targetAddress = bundle.getString(ARG_TARGET_ADDRESS),
        tokenAddress = bundle.getString(ARG_TOKEN_ADDRESS),
        amount = bundle.getParcelableCompat<Coins>(ARG_AMOUNT_NANO)?.let(::normalizeAmount),
        text = bundle.getString(ARG_TEXT),
        nftAddress = bundle.getString(ARG_NFT_ADDRESS) ?: "",
        type = bundle.getEnum(ARG_TYPE, SendScreen.Companion.Type.Default),
        bin = bundle.getString(ARG_BIN)?.cellFromBase64(),
        from = bundle.getEnum(ARG_FROM, Events.SendNative.SendNativeFrom.WalletScreen)
    )

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        targetAddress?.let { bundle.putString(ARG_TARGET_ADDRESS, it) }
        bundle.putString(ARG_TOKEN_ADDRESS, tokenAddress)
        amount?.let { bundle.putParcelable(ARG_AMOUNT_NANO, it) }
        text?.let { bundle.putString(ARG_TEXT, it) }
        bundle.putString(ARG_NFT_ADDRESS, nftAddress)
        bundle.putEnum(ARG_TYPE, type)
        bin?.let {
            bundle.putString(ARG_BIN, it.base64())
        }
        bundle.putEnum(ARG_FROM, from)
        return bundle
    }
}