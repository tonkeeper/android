package com.tonapps.tonkeeper.password

import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.tonapps.extensions.isMainVersion
import com.tonapps.tonkeeper.ui.component.PasscodeView
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import uikit.base.BaseDialog
import uikit.dialog.alert.AlertDialog
import uikit.widget.HeaderView

class PasscodeDialog(
    context: Context,
    private val callback: (success: Boolean) -> Unit
): BaseDialog(context) {

    private val passcodeDataStore: PasscodeDataStore by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val accountRepository: AccountRepository by inject()

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
            val valid = if (context.isMainVersion && !settingsRepository.importLegacyPasscode) {
                importLegacyPasscode(code)
            } else {
                passcodeDataStore.compare(code)
            }
            if (!valid) {
                passcodeView.setError()
                return@launch
            }
            passcodeView.setSuccess()
            callback(true)
            dismissAndDestroy()
        }
    }

    private suspend fun importLegacyPasscode(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            accountRepository.importPrivateKeysFromRNLegacy(code)
            passcodeDataStore.setPinCode(code)
            settingsRepository.importLegacyPasscode = true
            true
        } catch (e: Throwable) {
            false
        }
    }

}