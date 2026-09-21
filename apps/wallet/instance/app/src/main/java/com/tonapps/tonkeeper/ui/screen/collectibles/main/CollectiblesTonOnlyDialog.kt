package com.tonapps.tonkeeper.ui.screen.collectibles.main

import android.view.View
import android.widget.Button
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.base.BaseFragment
import uikit.dialog.modal.ModalDialog
import uikit.widget.TextHeaderView

class CollectiblesTonOnlyDialog(
    fragment: BaseFragment,
) : ModalDialog(fragment.requireContext(), R.layout.dialog_collectibles_ton_only) {

    private val textView = findViewById<TextHeaderView>(R.id.text)!!

    init {
        findViewById<View>(R.id.close)!!.setOnClickListener { dismiss() }
        findViewById<Button>(R.id.ok)!!.setOnClickListener { dismiss() }
        textView.titleView.setText(Localization.collectibles_ton_only_dialog_title)
        textView.descriptionView.setText(Localization.collectibles_ton_only_dialog_message)
    }
}
