package com.tonapps.signer.screen.sign.list

import androidx.annotation.StringRes
import com.tonapps.signer.R
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell

sealed class SignItem(
    type: Int,
    val position: ListCell.Position
): BaseListItem(type) {

    companion object {
        const val UNKNOWN = 1
        const val SEND = 2
        const val WALLET_ACTION = 3
    }

    class Unknown(position: ListCell.Position): SignItem(UNKNOWN, position)

    class Send(
        val target: String,
        val value: String,
        val sendsEntireBalance: Boolean,
        val destroysContract: Boolean,
        val tokenAddress: String?,
        val nftAddress: String?,
        val excessAddress: String?,
        val unknownOpCode: String?,
        val comment: String?,
        position: ListCell.Position
    ): SignItem(SEND, position) {

        val dangerous: Boolean
            get() = sendsEntireBalance || destroysContract
    }

    class WalletAction(
        val actionType: WalletActionType,
        val address: String?,
        val value: String?,
        position: ListCell.Position
    ): SignItem(WALLET_ACTION, position)

    enum class WalletActionType(
        @StringRes val title: Int,
        @StringRes val warning: Int?
    ) {
        ADD_EXTENSION(R.string.sign_set_extension, R.string.sign_extension_warning),
        REMOVE_EXTENSION(R.string.sign_remove_extension, null),
        ENABLE_SIGNATURE_AUTH(R.string.sign_enable_signature_auth, null),
        DISABLE_SIGNATURE_AUTH(R.string.sign_disable_signature_auth, R.string.sign_extension_warning),
        INSTALL_PLUGIN(R.string.sign_install_plugin, R.string.sign_plugin_warning),
        REMOVE_PLUGIN(R.string.sign_remove_plugin, null),
    }
}
