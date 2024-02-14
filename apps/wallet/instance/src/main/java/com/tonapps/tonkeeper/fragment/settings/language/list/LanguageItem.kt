package com.tonapps.tonkeeper.fragment.settings.language.list

data class LanguageItem(
    val name: String,
    val nameLocalized: String = "",
    val selected: Boolean = false,
    val code: String,
    override val position: com.tonapps.uikit.list.ListCell.Position
): com.tonapps.uikit.list.BaseListItem(), com.tonapps.uikit.list.ListCell