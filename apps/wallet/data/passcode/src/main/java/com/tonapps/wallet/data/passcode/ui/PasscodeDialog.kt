package com.tonapps.wallet.data.passcode.ui

import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.tonapps.wallet.data.passcode.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.base.BaseDialog
import uikit.widget.HeaderView

class PasscodeDialog(
    context: Context
): BaseDialog(context) {

    var callback: ((code: String) -> Unit)? = null

    private val headerView: HeaderView
    private val passcodeView: PasscodeView

    init {
        setContentView(R.layout.dialog_password)
        headerView = findViewById(R.id.header)
        headerView.doOnCloseClick = { dismiss() }

        passcodeView = findViewById(R.id.passcode)
        passcodeView.doOnCheck = { code ->
            passcodeView.isEnabled = false
            callback?.invoke(code)
        }
    }

    override fun dismiss() {
        callback?.invoke("")
        super.dismissAndDestroy()
    }

    suspend fun setError() = withContext(Dispatchers.Main) {
        passcodeView.setError()
    }

    suspend fun setSuccess() = withContext(Dispatchers.Main) {
        passcodeView.setSuccess()
        delay(300)
        close()
    }

    suspend fun close() = withContext(Dispatchers.Main) {
        dismissAndDestroy()
    }


}