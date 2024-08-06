package com.tonapps.tonkeeper.ui.screen.dialog.encrypted

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import com.tonapps.tonkeeperx.R
import kotlinx.coroutines.suspendCancellableCoroutine
import uikit.dialog.modal.ModalDialog
import uikit.widget.CheckBoxView
import uikit.widget.HeaderView
import kotlin.coroutines.resume

class EncryptedCommentScreen(context: Context): ModalDialog(context, R.layout.fragment_encrypted_comment) {

    companion object {

        suspend fun show(context: Context): Boolean? = suspendCancellableCoroutine { continuation ->
            val dialog = EncryptedCommentScreen(context)
            dialog.setOnDismissListener {
                continuation.resume(dialog.noShowAgain)
            }
            dialog.show()
        }
    }

    private lateinit var headerView: HeaderView
    private lateinit var button: Button
    private lateinit var checkboxContainerView: View
    private lateinit var checkboxView: CheckBoxView

    var noShowAgain: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        headerView = findViewById(R.id.header)!!
        headerView.doOnActionClick = { dismiss() }

        button = findViewById(R.id.decrypted_button)!!

        checkboxContainerView = findViewById(R.id.checkbox_container)!!
        checkboxView = findViewById(R.id.checkbox)!!

        checkboxContainerView.setOnClickListener { checkboxView.toggle() }

        button.setOnClickListener {
            noShowAgain = checkboxView.checked
            dismiss()
        }
    }
}