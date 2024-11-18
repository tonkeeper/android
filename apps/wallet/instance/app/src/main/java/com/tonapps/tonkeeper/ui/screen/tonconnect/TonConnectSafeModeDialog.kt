package com.tonapps.tonkeeper.ui.screen.tonconnect

import android.content.Context
import android.view.View
import com.tonapps.tonkeeper.ui.screen.settings.security.SecurityScreen
import com.tonapps.tonkeeperx.R
import uikit.dialog.modal.ModalDialog
import uikit.navigation.Navigation
import uikit.widget.ModalHeader

class TonConnectSafeModeDialog(context: Context): ModalDialog(context, R.layout.dialog_tonconnect_safemode) {

    private val navigation: Navigation? by lazy {
        Navigation.from(context)
    }

    init {
        findViewById<ModalHeader>(R.id.header)!!.onCloseClick = { dismiss() }
        findViewById<View>(R.id.open_settings)!!.setOnClickListener {
            dismiss()
            navigation?.add(SecurityScreen.newInstance())
        }

        findViewById<View>(R.id.cancel)!!.setOnClickListener {
            dismiss()
        }
    }


}