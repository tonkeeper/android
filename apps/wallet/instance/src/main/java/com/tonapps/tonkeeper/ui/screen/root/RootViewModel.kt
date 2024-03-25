package com.tonapps.tonkeeper.ui.screen.root

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.core.deeplink.DeepLink
import com.tonapps.tonkeeper.core.signer.SingerArgs
import com.tonapps.tonkeeper.core.tonconnect.TonConnect
import com.tonapps.tonkeeper.core.tonconnect.models.TCRequest
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.WalletSource
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

class RootViewModel(
    private val passcodeRepository: PasscodeRepository,
    private val settingsRepository: SettingsRepository,
    private val walletRepository: WalletRepository
): ViewModel() {

    val hasWalletFlow = walletRepository.walletsFlow.map { it.isNotEmpty() }.distinctUntilChanged()

    private val _eventFlow = MutableSharedFlow<RootEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    private val _lockFlow = MutableStateFlow<Boolean?>(null)
    val lockFlow = _lockFlow.asStateFlow().filterNotNull()

    val themeId: Int
        get() {
            return if (settingsRepository.theme == "dark") {
                uikit.R.style.Theme_App_Dark
            } else {
                uikit.R.style.Theme_App_Blue
            }
        }

    val themeFlow: Flow<Int> = settingsRepository.themeFlow.map {
        if (it == "dark") {
            uikit.R.style.Theme_App_Dark
        } else {
            uikit.R.style.Theme_App_Blue
        }
    }.drop(1)

    init {
        _lockFlow.value = settingsRepository.lockScreen && passcodeRepository.hasPinCode
    }

    fun checkPasscode(code: String): Flow<Unit> = flow {
        val valid = passcodeRepository.compare(code)
        if (valid) {
            _lockFlow.value = false
            emit(Unit)
        } else {
            throw Exception("invalid passcode")
        }
    }.take(1)

    fun processDeepLink(uri: Uri, fromQR: Boolean): Boolean {
        if (DeepLink.isSupportedUri(uri)) {
            resolveDeepLink(uri, fromQR)
            return true
        }
        return false
    }

    private fun resolveDeepLink(uri: Uri, fromQR: Boolean) {
        if (uri.host == "signer") {
            resolveSignerLink(uri, fromQR)
            return
        }
        walletRepository.activeWalletFlow.take(1).onEach { wallet ->
            resolveOther(uri, wallet)
        }.launchIn(viewModelScope)
    }

    private fun resolveSignerLink(uri: Uri, fromQR: Boolean) {
        try {
            val args = SingerArgs(uri)
            val walletSource = if (fromQR) {
                WalletSource.SingerQR
            } else {
                WalletSource.SingerApp
            }
            _eventFlow.tryEmit(RootEvent.Singer(args.publicKeyEd25519, args.name, walletSource))
        } catch (e: Throwable) {
            toast(Localization.invalid_link)
        }
    }

    private fun resolveOther(uri: Uri, wallet: WalletEntity) {
        val url = uri.toString()
        if (TonConnect.isSupportUri(uri)) {
            resolveTonConnect(uri, wallet)
        } else {
            toast(Localization.invalid_link)
        }

        /*
        if (url == HistoryScreen.DeepLink) {
            _eventFlow.tryEmit(RootEvent.OpenTab(R.id.activity))
        } else
         */
    }

    private fun resolveTonConnect(uri: Uri, wallet: WalletEntity) {
        try {
            if (!wallet.hasPrivateKey) {
                toast(Localization.not_supported)
                return
            }
            val request = TCRequest(uri)
            _eventFlow.tryEmit(RootEvent.TonConnect(request))
        } catch (e: Throwable) {
            toast(Localization.invalid_link)
        }
    }

    private fun toast(resId: Int) {
        _eventFlow.tryEmit(RootEvent.Toast(resId))
    }
}