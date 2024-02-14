package com.tonapps.tonkeeper.fragment.settings.accounts.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonapps.tonkeeper.fragment.settings.accounts.list.item.AccountsItem
import uikit.extensions.inflate

abstract class AccountsHolder<I: AccountsItem>(
    view: View
): com.tonapps.uikit.list.BaseListHolder<I>(view) {

    constructor(
        parent: ViewGroup,
        @LayoutRes resId: Int,
    ) : this(
        parent.inflate(resId)
    )
}
