package com.tonapps.tonkeeper.ui.screen.settings.theme.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonapps.tonkeeper.ui.screen.settings.theme.list.Item
import com.tonapps.uikit.list.BaseListHolder
import uikit.extensions.inflate

abstract class Holder<I: Item>(view: View): BaseListHolder<I>(view) {

    constructor(
        parent: ViewGroup,
        @LayoutRes resId: Int
    ): this(parent.context.inflate(resId, parent, false))
}