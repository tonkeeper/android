package com.tonapps.tonkeeper.ui.screen.add.imprt.list

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.localization.Localization

data class Item(
    val id: Int,
    @DrawableRes val iconResId: Int,
    @StringRes val titleResId: Int,
    @StringRes val subtitleResId: Int
): BaseListItem() {

    companion object {
        const val IMPORT_WALLET_ID = 1
        const val WATCH_WALLET_ID = 2
        const val TESTNET_WALLET_ID = 3
        const val SIGNER_WALLET_ID = 4
        const val LEDGER_WALLET_ID = 5

        val import = Item(
            id = IMPORT_WALLET_ID,
            iconResId = UIKitIcon.ic_key_28,
            titleResId = Localization.import_wallet,
            subtitleResId = Localization.import_wallet_words
        )

        val watch = Item(
            id = WATCH_WALLET_ID,
            iconResId = R.drawable.ic_magnifying_glass_28,
            titleResId = Localization.watch_wallet,
            subtitleResId = Localization.watch_wallet_subtitle
        )

        val testnet = Item(
            id = TESTNET_WALLET_ID,
            iconResId = R.drawable.ic_testnet_28,
            titleResId = Localization.testnet_wallet,
            subtitleResId = Localization.testnet_wallet_subtitle
        )

        val signer = Item(
            id = SIGNER_WALLET_ID,
            iconResId = UIKitIcon.ic_key_28,
            titleResId = Localization.signer_wallet,
            subtitleResId = Localization.signer_wallet_subtitle
        )

        val ledger = Item(
            id = LEDGER_WALLET_ID,
            iconResId = R.drawable.ic_ledger_28,
            titleResId = Localization.ledger_title,
            subtitleResId = Localization.ledger_subtitle
        )
    }

}