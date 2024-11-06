package com.tonapps.tonkeeper.ui.screen.add.list

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.localization.Localization

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_WALLET = -10
        const val TYPE_HEADER = -11

        const val IMPORT_WALLET_ID = 1
        const val WATCH_WALLET_ID = 2
        const val TESTNET_WALLET_ID = 3
        const val SIGNER_WALLET_ID = 4
        const val LEDGER_WALLET_ID = 5
        const val KEYSTONE_WALLET_ID = 6
        const val NEW_WALLET_ID = 7

        fun header(title: Int, subtitle: Int): Header {
            return Header(title, subtitle)
        }

        val new = Wallet(
            id = NEW_WALLET_ID,
            iconResId = R.drawable.ic_plus_circle_28,
            titleResId = Localization.new_wallet,
            subtitleResId = Localization.start_create_new_wallet
        )

        val import = Wallet(
            id = IMPORT_WALLET_ID,
            iconResId = UIKitIcon.ic_key_28,
            titleResId = Localization.import_wallet,
            subtitleResId = Localization.import_wallet_words
        )

        val watch = Wallet(
            id = WATCH_WALLET_ID,
            iconResId = R.drawable.ic_magnifying_glass_28,
            titleResId = Localization.watch_wallet,
            subtitleResId = Localization.watch_wallet_subtitle
        )

        val testnet = Wallet(
            id = TESTNET_WALLET_ID,
            iconResId = R.drawable.ic_testnet_28,
            titleResId = Localization.testnet_wallet,
            subtitleResId = Localization.testnet_wallet_subtitle
        )

        val signer = Wallet(
            id = SIGNER_WALLET_ID,
            iconResId = UIKitIcon.ic_key_28,
            titleResId = Localization.signer_wallet,
            subtitleResId = Localization.signer_wallet_subtitle
        )

        val keystone = Wallet(
            id = KEYSTONE_WALLET_ID,
            iconResId = UIKitIcon.ic_keystone_28,
            titleResId = Localization.keystone_title,
            subtitleResId = Localization.keystone_subtitle
        )

        val ledger = Wallet(
            id = LEDGER_WALLET_ID,
            iconResId = R.drawable.ic_ledger_28,
            titleResId = Localization.ledger_title,
            subtitleResId = Localization.ledger_subtitle
        )
    }

    data class Wallet(
        val id: Int,
        @DrawableRes val iconResId: Int,
        @StringRes val titleResId: Int,
        @StringRes val subtitleResId: Int
    ): Item(TYPE_WALLET)

    data class Header(
        @StringRes val titleResId: Int,
        @StringRes val subtitleResId: Int
    ): Item(TYPE_HEADER)

}