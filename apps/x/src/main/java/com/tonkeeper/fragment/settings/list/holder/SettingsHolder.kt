package com.tonkeeper.fragment.settings.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import uikit.extensions.inflate
import uikit.list.BaseListHolder

abstract class SettingsHolder<I: SettingsItem>(
    view: View,
    val onClick: ((SettingsItem, View) -> Unit)? = null
): BaseListHolder<I>(view) {

    constructor(
        parent: ViewGroup,
        @LayoutRes resId: Int,
        onClick: ((SettingsItem, View) -> Unit)? = null
    ) : this(
        parent.inflate(resId),
        onClick
    )

}