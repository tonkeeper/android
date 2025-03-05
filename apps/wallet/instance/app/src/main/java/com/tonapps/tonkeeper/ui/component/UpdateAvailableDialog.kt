package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import com.tonapps.tonkeeperx.R
import uikit.dialog.modal.ModalDialog
import uikit.widget.HeaderView

class UpdateAvailableDialog(context: Context): ModalDialog(context, R.layout.dialog_update_available) {

    init {
        findViewById<View>(R.id.later)!!.setOnClickListener { dismiss() }
        findViewById<HeaderView>(R.id.header)!!.doOnActionClick = { dismiss() }
    }

    fun show(callback: () -> Unit) {
        super.show()
        findViewById<View>(R.id.update)!!.setOnClickListener {
            callback()
            dismiss()
        }
    }
}