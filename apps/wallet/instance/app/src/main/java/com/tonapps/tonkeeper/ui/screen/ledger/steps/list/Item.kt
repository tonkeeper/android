package com.tonapps.tonkeeper.ui.screen.ledger.steps.list

import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_STEP = 0
    }

    data class Step(
        val label: String,
        val isDone: Boolean,
        val isCurrent: Boolean,
        val showInstallTon: Boolean = false
    ): Item(TYPE_STEP)

}