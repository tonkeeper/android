package com.tonapps.wallet.data.passcode.dialog

import android.content.Context
import android.os.Bundle
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
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

    private val windowInsetsController: WindowInsetsControllerCompat? by lazy {
        val window = window ?: return@lazy null
        WindowInsetsControllerCompat(window, window.decorView)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSecure(true)
        setAppearanceLight(helper.isLightTheme)
    }

    override fun dismiss() {
        setSecure(false)
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

    private fun setAppearanceLight(light: Boolean) {
        windowInsetsController?.apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

}