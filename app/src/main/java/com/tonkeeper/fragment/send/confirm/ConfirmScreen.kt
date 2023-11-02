package com.tonkeeper.fragment.send.confirm

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.viewModels
import com.facebook.drawee.view.SimpleDraweeView
import com.tonkeeper.R
import com.tonkeeper.extensions.setImageRes
import com.tonkeeper.fragment.send.pager.PagerScreen

class ConfirmScreen: PagerScreen<ConfirmScreenState, ConfirmScreenEffect, ConfirmScreenFeature>(R.layout.fragment_send_confirm) {

    companion object {
        fun newInstance() = ConfirmScreen()
    }

    override val feature: ConfirmScreenFeature by viewModels()

    private lateinit var iconView: SimpleDraweeView
    private lateinit var titleView: AppCompatTextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        iconView = view.findViewById(R.id.icon)
        iconView.setImageRes(R.drawable.ic_toncoin)

        titleView = view.findViewById(R.id.title)
    }

    override fun newUiState(state: ConfirmScreenState) {

    }
}