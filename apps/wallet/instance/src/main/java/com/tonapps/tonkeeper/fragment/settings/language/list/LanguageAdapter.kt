package com.tonapps.tonkeeper.fragment.settings.language.list

import android.view.ViewGroup

class LanguageAdapter(
    private val onClick: (item: LanguageItem) -> Unit
): com.tonapps.uikit.list.BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): com.tonapps.uikit.list.BaseListHolder<out com.tonapps.uikit.list.BaseListItem> {
        return LanguageHolder(parent, onClick)
    }
}