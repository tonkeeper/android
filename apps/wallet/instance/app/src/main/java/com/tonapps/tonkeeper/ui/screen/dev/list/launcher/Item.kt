package com.tonapps.tonkeeper.ui.screen.dev.list.launcher

import android.content.Context
import androidx.annotation.DrawableRes
import com.tonapps.tonkeeper.core.LauncherIcon
import com.tonapps.uikit.list.BaseListItem

data class Item(val icon: LauncherIcon): BaseListItem() {

    @get:DrawableRes
    val iconRes: Int
        get() = icon.iconRes

    val title: String
        get() = icon.type

    fun isEnabled(context: Context): Boolean {
        return icon.isEnabled(context)
    }
}