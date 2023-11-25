package com.tonkeeper.fragment.settings.accounts.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonkeeper.fragment.settings.accounts.list.item.AccountsItem
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import uikit.extensions.inflate
import uikit.list.BaseListHolder

abstract class AccountsHolder<I: AccountsItem>(
    view: View
): BaseListHolder<I>(view) {

    constructor(
        parent: ViewGroup,
        @LayoutRes resId: Int,
    ) : this(
        parent.inflate(resId)
    )
}
