package com.tonkeeper.fragment.settings.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonkeeper.uikit.list.BaseListHolder

abstract class SettingsHolder<I: SettingsItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): BaseListHolder<I>(parent, resId)