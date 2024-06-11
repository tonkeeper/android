package com.tonapps.tonkeeper.password

import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.ui.component.PasscodeView
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import uikit.base.BaseDialog
import uikit.dialog.alert.AlertDialog
import uikit.widget.HeaderView

class PasscodeDialog(
    context: Context,
    private val callback: (success: Boolean) -> Unit
): BaseDialog(context) {

    private val passcodeDataStore: PasscodeDataStore by inject()

    private val headerView: HeaderView
    private val passcodeView: PasscodeView

    init {
        setContentView(R.layout.dialog_password)
        headerView = findViewById(R.id.header)
        headerView.doOnCloseClick = { dismiss() }

        passcodeView = findViewById(R.id.passcode)
        passcodeView.doOnCheck = {
            checkValues(it)
        }
    }

    override fun dismiss() {
        callback(false)
        super.dismissAndDestroy()
    }

    private fun checkValues(code: String) {
        passcodeView.isEnabled = false
        lifecycleScope.launch(Dispatchers.Main) {
            val valid = passcodeDataStore.compare(code)
            if (!valid) {
                passcodeView.setError()
                return@launch
            }
            passcodeView.setSuccess()
            callback(true)
            dismissAndDestroy()
        }
    }
}