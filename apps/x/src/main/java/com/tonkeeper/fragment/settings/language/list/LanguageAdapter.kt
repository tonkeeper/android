package com.tonkeeper.fragment.settings.language.list

import android.view.ViewGroup
import uikit.list.BaseListAdapter
import uikit.list.BaseListHolder
import uikit.list.BaseListItem

class LanguageAdapter(
    private val onClick: (item: LanguageItem) -> Unit
): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return LanguageHolder(parent, onClick)
    }
}