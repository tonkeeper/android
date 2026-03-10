package com.tonapps.tonkeeper.ui.screen.onramp.picker.provider.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import androidx.core.net.toUri
import com.tonapps.tonkeeper.ui.screen.onramp.main.entities.ProviderEntity
import com.tonapps.wallet.api.entity.OnRampMerchantEntity

data class Item(
    val position: ListCell.Position,
    val id: String,
    val iconUri: Uri,
    val title: String,
    val description: CharSequence,
    val selected: Boolean,
    val best: Boolean,
    val minAmountFormat: CharSequence
): BaseListItem(0) {

    constructor(
        position: ListCell.Position,
        provider: ProviderEntity,
        selected: Boolean,
        best: Boolean,
        rateFormat: CharSequence,
        minAmountFormat: CharSequence
    ) : this(
        position = position,
        id = provider.id,
        iconUri = provider.iconUrl.toUri(),
        title = provider.title,
        description = rateFormat,
        selected = selected,
        best = best,
        minAmountFormat = minAmountFormat
    )
}
