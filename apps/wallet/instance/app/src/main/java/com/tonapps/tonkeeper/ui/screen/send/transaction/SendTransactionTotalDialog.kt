package com.tonapps.tonkeeper.ui.screen.send.transaction

import android.content.Context
import android.view.View
import android.widget.Button
import com.tonapps.tonkeeperx.R
import uikit.dialog.modal.ModalDialog
import uikit.widget.TextHeaderView

class SendTransactionTotalDialog(context: Context): ModalDialog(context, R.layout.dialog_send_transaction_total) {

    private val closeView = findViewById<View>(R.id.close)!!
    private val headerView = findViewById<TextHeaderView>(R.id.header)!!
    private val buttonView = findViewById<Button>(R.id.button)!!

    init {
        closeView.setOnClickListener { dismiss() }
        buttonView.setOnClickListener { dismiss() }
    }

    fun show(title: String, description: String) {
        super.show()
        headerView.title = title
        headerView.desciption = description
    }
}