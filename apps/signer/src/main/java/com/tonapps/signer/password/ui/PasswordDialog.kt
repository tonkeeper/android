package com.tonapps.signer.password.ui

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import com.tonapps.signer.BuildConfig
import com.tonapps.signer.R
import com.tonapps.signer.SimpleState
import com.tonapps.signer.vault.SignerVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import uikit.base.BaseDialog
import uikit.widget.HeaderView
import javax.crypto.SecretKey

class PasswordDialog(
    context: Context,
    private val callback: (masterSecret: SecretKey) -> Unit
): BaseDialog(context) {

    private val vault: SignerVault by inject()

    private val rootView: View
    private val headerView: HeaderView
    private val passwordView: PasswordView

    private var job: Job? = null

    init {
        if (!BuildConfig.DEBUG) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        setContentView(R.layout.dialog_password)

        rootView = findViewById(R.id.root)
        rootView.setOnClickListener {  }

        headerView = findViewById(R.id.header)
        headerView.doOnCloseClick = { dismiss() }

        passwordView = findViewById(R.id.password)
        passwordView.doOnPassword = ::getPrivateKey
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        rootView.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
    }

    private fun getPrivateKey(password: CharArray) {
        setState(SimpleState.Loading)

        job?.cancel()
        job = lifecycleScope.launch {
            try {
                val masterSecret = getMasterSecret(password)
                setState(SimpleState.Success)
                setSuccess(masterSecret)
            } catch (e: Throwable) {
                setState(SimpleState.Error)
            }
        }
    }

    private suspend fun getMasterSecret(password: CharArray) = withContext(Dispatchers.IO) {
        vault.getMasterSecret(password)
    }

    private fun setSuccess(masterSecret: SecretKey) {
        callback(masterSecret)
        dismiss()
    }

    private fun setState(state: SimpleState) {
        when (state) {
            SimpleState.Default -> passwordView.applyDefaultState()
            SimpleState.Error -> passwordView.applyErrorState()
            SimpleState.Success -> passwordView.applySuccessState()
            SimpleState.Loading -> passwordView.applyLoadingState()
        }
    }

    override fun show() {
        super.show()
        passwordView.reset()
    }

    override fun onResume() {
        super.onResume()
        passwordView.focus()
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }
}