package com.tonapps.signer.screen.storage

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.tonapps.signer.R
import com.tonapps.signer.password.Password
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.color.accentRedColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseActivity
import uikit.dialog.alert.AlertDialog

class StorageActivity: BaseActivity() {

    private val storageViewModel: StorageViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            if (storageViewModel.hasPassword()) {
                showSignOutDialog()
            } else {
                finish()
            }
        }
    }

    private fun showSignOutDialog() {
        var isSignOutConfirmed = false
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.sign_out_question)
            .setMessage(R.string.sign_out_subtitle)
            .setNegativeButton(R.string.cancel, accentBlueColor)
            .setPositiveButton(R.string.sign_out, accentRedColor) { isSignOutConfirmed = true }
            .build()
        dialog.setOnDismissListener {
            if (isSignOutConfirmed) {
                signOut()
            } else {
                finish()
            }
        }
        dialog.show()
    }

    private fun signOut() {
        storageViewModel.signOut(this).catch { throwable ->
            when (throwable) {
                is CancellationException -> throw throwable
                is Password.UserCancelThrowable -> finish()
                else -> showSignOutErrorDialog()
            }
        }.onEach {
            finish()
        }.launchIn(lifecycleScope)
    }

    private fun showSignOutErrorDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.unknown_error)
            .setPositiveButton(R.string.ok, accentBlueColor)
            .build()
        dialog.setOnDismissListener { finish() }
        dialog.show()
    }
}
