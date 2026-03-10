package com.tonapps.tonkeeper.ui.screen.battery.recharge.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonapps.tonkeeper.ui.screen.battery.recharge.list.Item

abstract class InputHolder<I: Item>(
    parent: ViewGroup,
    @LayoutRes resId: Int,
): Holder<I>(parent, resId) {

    abstract val inputFieldView: View

    init {
        setIsRecyclable(false)
    }

}