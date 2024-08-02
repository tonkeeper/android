package com.tonapps.tonkeeper.ui.screen.backup.main

import android.content.Context
import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeperx.R
import uikit.dialog.modal.ModalDialog
import uikit.widget.HeaderView

class BackupAttentionDialog(
    context: Context
): ModalDialog(context, R.layout.fragment_backup_attention) {

    private lateinit var confirmButton: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<View>(R.id.cancel_button)?.setOnClickListener { dismiss() }
        findViewById<HeaderView>(R.id.header)?.doOnActionClick = { dismiss() }
        confirmButton = findViewById(R.id.continue_button)!!
    }

    fun show(onClock: () -> Unit) {
        super.show()
        confirmButton.setOnClickListener {
            onClock()
            dismiss()
        }
    }
}