package com.tonapps.tonkeeper.ui.screen.settings.security

import android.content.Context
import android.view.View
import com.tonapps.tonkeeperx.R
import uikit.dialog.modal.ModalDialog
import uikit.extensions.setMoonOnClickListener

class TurnOffLockScreenDialog(
    context: Context,
    private val onTurnOff: () -> Unit,
) : ModalDialog(context, R.layout.dialog_turn_off_lock_screen) {

    init {
        findViewById<View>(R.id.close)!!.setMoonOnClickListener { dismiss() }
        findViewById<View>(R.id.keep_on)!!.setMoonOnClickListener { dismiss() }
        findViewById<View>(R.id.turn_off)!!.setMoonOnClickListener {
            onTurnOff()
            dismiss()
        }
    }
}
