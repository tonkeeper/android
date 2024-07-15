package com.tonapps.tonkeeper.ui.screen.events.list

import android.net.Uri
import com.facebook.common.util.UriUtil
import com.tonapps.extensions.uri
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.UIKitColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.entity.AccountEntity
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.localization.Localization

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_DATE = 0
        const val TYPE_ACTION = 1
        const val TYPE_SPACE = 2
    }

    data class Date(val date: String): Item(TYPE_DATE)

    open class Action(
        val position: ListCell.Position,
        val iconUri: Uri,
        val titleRes: Int,
        val subtitle: String,
        val comment: String? = null,
        val loading: Boolean = false,
        val value: CharSequence? = null,
        val value2: CharSequence? = null,
        val valueColorRef: Int = UIKitColor.textPrimaryColor,
        val nft: NftEntity? = null
    ): Item(TYPE_ACTION)

    class SendAction(
        position: ListCell.Position,
        account: AccountEntity,
        comment: String?,
        loading: Boolean,
        value: CharSequence,
        nft: NftEntity? = null
    ): Action(
        position = position,
        iconUri = account.iconUri ?: UIKitIcon.ic_tray_arrow_up_28.uri(),
        titleRes = Localization.sent,
        subtitle = account.accountName,
        comment = comment,
        loading = loading,
        value = value,
        nft = nft
    )

    class ReceiveAction(
        position: ListCell.Position,
        account: AccountEntity,
        comment: String?,
        loading: Boolean,
        value: CharSequence,
        nft: NftEntity? = null
    ): Action(
        position = position,
        iconUri = account.iconUri ?: R.drawable.ic_tray_arrow_down_28.uri(),
        titleRes = Localization.received,
        subtitle = account.accountName,
        comment = comment,
        loading = loading,
        value = value,
        valueColorRef = UIKitColor.accentGreenColor,
        nft = nft
    )

    class Swap(
        position: ListCell.Position,
        loading: Boolean,
        accountName: String,
        value: String,
        value2: String,
    ): Action(
        position = position,
        iconUri = R.drawable.ic_swap_horizontal_alternative_28.uri(),
        titleRes = Localization.swap,
        subtitle = accountName,
        loading = loading,
        value = value,
        valueColorRef = UIKitColor.accentGreenColor,
        value2 = value2
    )

    class UnknownAction(
        position: ListCell.Position,
    ): Action(
        position = position,
        iconUri = UIKitIcon.ic_gear_28.uri(),
        titleRes = Localization.unknown,
        subtitle = "",
        comment = null
    )

    data object Space: Item(TYPE_SPACE)
}