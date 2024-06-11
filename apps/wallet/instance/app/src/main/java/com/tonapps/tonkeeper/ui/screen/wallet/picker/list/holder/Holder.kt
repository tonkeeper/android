package com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Item
import com.tonapps.uikit.list.BaseListHolder
import uikit.navigation.Navigation
import uikit.navigation.Navigation.Companion.navigation

abstract class Holder<I: Item>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): BaseListHolder<I>(parent, resId) {

    val navigation: Navigation?
        get() = context.navigation

}
