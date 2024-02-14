package com.tonapps.tonkeeper.fragment.settings.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsItem
import uikit.extensions.inflate

abstract class SettingsHolder<I: SettingsItem>(
    view: View,
    val onClick: ((SettingsItem, View) -> Unit)? = null
): com.tonapps.uikit.list.BaseListHolder<I>(view) {

    constructor(
        parent: ViewGroup,
        @LayoutRes resId: Int,
        onClick: ((SettingsItem, View) -> Unit)? = null
    ) : this(
        parent.inflate(resId),
        onClick
    )

}