package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.view.View
import com.tonapps.tonkeeperx.R
import uikit.dialog.modal.ModalDialog

class UpdateAvailableDialog(context: Context): ModalDialog(context, R.layout.dialog_update_available) {

    init {
        findViewById<View>(R.id.later)!!.setOnClickListener { dismiss() }
    }

    fun show(callback: () -> Unit) {
        super.show()
        findViewById<View>(R.id.update)!!.setOnClickListener {
            callback()
            dismiss()
        }

    }
}