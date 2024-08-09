package com.tonapps.wallet.data.passcode.dialog

import android.content.Context
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.viewModel
import com.tonapps.wallet.data.passcode.PasscodeHelper
import com.tonapps.wallet.data.passcode.R
import com.tonapps.wallet.data.passcode.ui.PasscodeView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import uikit.base.BaseDialog
import uikit.widget.HeaderView
import kotlin.coroutines.resume

class PasscodeDialog(
    context: Context,
    private val callback: ((code: String?) -> Unit)
): BaseDialog(context) {

    companion object {

        fun request(context: Context, callback: ((code: String?) -> Unit)) {
            PasscodeDialog(context, callback).show()
        }

        suspend fun request(context: Context): String? = suspendCancellableCoroutine { continuation ->
            request(context) { code ->
                continuation.resume(code)
            }
        }

        suspend fun confirmation(context: Context): Boolean {
            return request(context) != null
        }
    }

    private var resultAlreadySent = false
    private val helper: PasscodeHelper by inject()
    private val headerView: HeaderView
    private val passcodeView: PasscodeView

    init {
        setContentView(R.layout.dialog_password)
        headerView = findViewById(R.id.header)
        headerView.doOnCloseClick = { dismiss() }

        passcodeView = findViewById(R.id.passcode)
        passcodeView.doOnCheck = { code ->
            passcodeView.isEnabled = false
            check(code)
        }
    }

    override fun dismiss() {
        setResult(null)
        super.dismissAndDestroy()
    }

    private fun check(code: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            val isValid = isValid(code)
            if (isValid) {
                setResult(code)
                passcodeView.setSuccess()
                delay(1000)
                dismissAndDestroy()
            } else {
                passcodeView.setError()
                passcodeView.isEnabled = true
            }
        }
    }

    private suspend fun isValid(code: String) = withContext(Dispatchers.IO) {
        helper.isValid(context, code)
    }

    private fun setResult(code: String?) {
        if (resultAlreadySent) {
            return
        }
        resultAlreadySent = true
        callback.invoke(code)
    }

}