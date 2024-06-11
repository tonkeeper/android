package com.tonapps.tonkeeper.ui.screen.init.list

import android.os.Parcelable
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import kotlinx.parcelize.Parcelize

@Parcelize
data class AccountItem(
    val address: String,
    val name: String?,
    val walletVersion: WalletVersion,
    val balanceFormat: CharSequence,
    val tokens: Boolean,
    val collectibles: Boolean,
    val selected: Boolean,
    val position: ListCell.Position
): BaseListItem(0), Parcelable