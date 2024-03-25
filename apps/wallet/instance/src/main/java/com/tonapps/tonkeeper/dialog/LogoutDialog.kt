package com.tonapps.tonkeeper.dialog

import android.content.Context
import android.view.View
import android.widget.Button
import com.tonapps.tonkeeperx.R
import uikit.base.BaseSheetDialog
import uikit.widget.CheckBoxView

class LogoutDialog(context: Context): BaseSheetDialog(context) {

    private val confirmationView: View
    private val checkboxView: CheckBoxView
    private val logoutButton: Button

    init {
        setContentView(R.layout.dialog_logout)
        confirmationView = findViewById(R.id.confirmation)!!
        checkboxView = findViewById(R.id.checkbox)!!
        logoutButton = findViewById(R.id.logout)!!

        confirmationView.setOnClickListener {
            checkboxView.toggle()
        }

        checkboxView.doOnCheckedChanged = {
            logoutButton.isEnabled = it
        }
    }

    fun show(onLogout: () -> Unit) {
        logoutButton.setOnClickListener {
            onLogout()
            dismiss()
        }
        super.show()
    }
}