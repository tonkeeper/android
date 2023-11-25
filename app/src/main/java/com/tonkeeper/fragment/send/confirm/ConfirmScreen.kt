package com.tonkeeper.fragment.send.confirm

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.fragment.app.viewModels
import com.facebook.drawee.view.SimpleDraweeView
import com.tonkeeper.R
import com.tonkeeper.api.shortAddress
import com.tonkeeper.core.Coin
import com.tonkeeper.extensions.setImageRes
import com.tonkeeper.fragment.send.pager.PagerScreen
import com.tonkeeper.fragment.send.view.ItemView
import uikit.list.ListCell

class ConfirmScreen: PagerScreen<ConfirmScreenState, ConfirmScreenEffect, ConfirmScreenFeature>(R.layout.fragment_send_confirm) {

    companion object {
        fun newInstance() = ConfirmScreen()
    }

    override val feature: ConfirmScreenFeature by viewModels()

    private lateinit var iconView: SimpleDraweeView
    private lateinit var titleView: AppCompatTextView
    private lateinit var itemsView: LinearLayoutCompat
    private lateinit var sendButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        iconView = view.findViewById(R.id.icon)
        iconView.setImageResource(R.drawable.ic_toncoin)

        titleView = view.findViewById(R.id.title)

        itemsView = view.findViewById(R.id.items)

        sendButton = view.findViewById(R.id.send)
        sendButton.setOnClickListener {
            feature.send()
        }
    }

    override fun newUiState(state: ConfirmScreenState) {
        initItems(state.items)
    }

    private fun initItems(items: List<ConfirmScreenState.Item>) {
        itemsView.removeAllViews()

        for ((index, item) in items.withIndex()) {
            val itemView = ItemView(requireContext())
            itemView.title = item.title
            itemView.value = item.value
            itemView.description = item.description
            itemView.position = ListCell.getPosition(items.size, index)
            itemsView.addView(itemView, LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT
            ))
        }
    }
    override fun onVisibleChange(visible: Boolean) {
        super.onVisibleChange(visible)
        if (visible) {
            parentScreen?.hideText()
            feature.updateItems(requireContext(), parentFeature?.recipient, parentFeature?.amount)
        } else {
            parentScreen?.showText()
        }
    }
}