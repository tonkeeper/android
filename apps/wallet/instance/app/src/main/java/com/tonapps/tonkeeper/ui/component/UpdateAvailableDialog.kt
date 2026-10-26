package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.os.Bundle
import com.tonapps.log.L
import android.view.View
import com.tonapps.tonkeeper.manager.apk.APKManager
import com.tonapps.tonkeeperx.R
import uikit.dialog.modal.ModalDialog
import uikit.widget.HeaderView
import uikit.extensions.setMoonOnClickListener

class UpdateAvailableDialog(
    context: Context,
    private val apkManager: APKManager,
): ModalDialog(context, R.layout.dialog_update_available) {

    init {
        findViewById<View>(R.id.later)!!.setMoonOnClickListener { closeAndClick() }
        findViewById<HeaderView>(R.id.header)!!.doOnActionClick = { closeAndClick() }
    }

    fun show(callback: () -> Unit) {
        super.show()
        findViewById<View>(R.id.update)!!.setMoonOnClickListener {
            callback()
            dismiss()
        }
    }

    private fun closeAndClick() {
        apkManager.closeReminder()
        dismiss()
    }
}