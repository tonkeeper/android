package com.tonapps.signer.screen.main.list.holder

import android.view.ViewGroup
import com.tonapps.signer.R
import com.tonapps.signer.extensions.short8
import com.tonapps.signer.screen.main.list.MainItem
import uikit.list.ListCell.Companion.drawable
import uikit.widget.ActionCellView

class MainAccountHolder(
    parent: ViewGroup,
    private val selectAccountCallback: (id: Long) -> Unit
): MainHolder<MainItem.Account>(parent, R.layout.view_main_account) {

    private val actionView = itemView as ActionCellView

    override fun onBind(item: MainItem.Account) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener {
            selectAccountCallback(item.id)
        }

        actionView.title = item.label
        actionView.subtitle = item.hex.short8
    }

}