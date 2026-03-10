package com.tonapps.tonkeeper.ui.screen.transaction

import android.content.Context
import android.view.View
import com.tonapps.tonkeeperx.R
import kotlinx.coroutines.suspendCancellableCoroutine
import uikit.dialog.modal.ModalDialog
import kotlin.coroutines.resume

class CommentReportDialog(context: Context) : ModalDialog(context, R.layout.dialog_tx_report_comment) {

    companion object {

        suspend fun show(context: Context) = suspendCancellableCoroutine { continuation ->
            CommentReportDialog(context).show {
                continuation.resume(Unit)
            }
        }
    }

    init {
        findViewById<View>(R.id.close)?.setOnClickListener { dismiss() }
    }

    fun show(callback: () -> Unit) {
        super.show()
        findViewById<View>(R.id.button)?.setOnClickListener {
            callback()
            dismiss()
        }
    }
}