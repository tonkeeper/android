package com.tonapps.tonkeeper.ui.screen.external.qr.signer.add

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletSource
import com.tonapps.bus.walletimport.WalletImportPairingSession
import com.tonapps.bus.walletimport.WalletImportPairingSessions
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.core.signer.SignerApp
import com.tonapps.tonkeeper.extensions.failureToast
import com.tonapps.tonkeeper.ui.base.QRCameraScreen
import com.tonapps.tonkeeper.ui.component.CameraFlashIconView
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeper.ui.screen.init.WalletImportAnalytics
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.constantWhiteColor
import com.tonapps.uikit.color.stateList
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject
import org.ton.kotlin.crypto.PublicKeyEd25519
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.pinToBottomInsets
import uikit.extensions.withAlpha
import uikit.navigation.Navigation.Companion.navigation
import uikit.extensions.setMoonOnClickListener

class SignerAddScreen: QRCameraScreen(R.layout.fragment_signer_add), BaseFragment.BottomSheet {

    override val fragmentName: String = "SignerAddScreen"

    override lateinit var cameraView: PreviewView

    private val importAnalytics: WalletImportAnalytics by inject()

    private val pairingSessions: WalletImportPairingSessions by inject()

    private val pairingSession: WalletImportPairingSession? by lazy {
        pairingSessions.active(WalletFlowWalletSource.Signer)
    }

    private var isHandedOff = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val constantWhiteColor = requireContext().constantWhiteColor.withAlpha(.08f).stateList

        val closeView = view.findViewById<View>(R.id.close)
        closeView.setMoonOnClickListener { finish() }
        closeView.backgroundTintList = constantWhiteColor

        val aboutView = view.findViewById<View>(R.id.about)
        aboutView.backgroundTintList = constantWhiteColor
        aboutView.setMoonOnClickListener {
            BrowserHelper.open(requireActivity(), "https://tonkeeper.com/signer")
        }

        cameraView = view.findViewById(R.id.camera)

        val flashView = view.findViewById<CameraFlashIconView>(R.id.flash)
        flashView.setMoonOnClickListener { toggleFlash() }

        val signerOpenButton = view.findViewById<View>(R.id.signer_open)
        signerOpenButton.pinToBottomInsets()
        signerOpenButton.setMoonOnClickListener {
            pairingSession?.signerAppOpened()
            isHandedOff = true
            SignerApp.openAppOrInstall(requireContext())
            finish()
        }

        collectFlow(flashConfigFlow) { flashConfig ->
            if (!flashConfig.isFlashAvailable) {
                flashView.visibility = View.GONE
            } else {
                flashView.setFlashState(flashConfig.isFlashEnabled)
            }
        }

        readerFlow.map { Uri.parse(it) }.filter { it.host == "signer" }.catch {
            navigation?.failureToast(Localization.unknown_error)
            trackImportError("signer_qr_scan_error", it.message)
        }.onEach { uri ->
            val pk = uri.getQueryParameter("pk")
                ?.let { value -> runCatching { value.publicKeyFromHex() }.getOrNull() }
                ?: run {
                    trackImportError("signer_qr_invalid")
                    return@onEach
                }
            val name = uri.getQueryParameter("name") ?: ""
            pairingSession?.connected()
            importAnalytics.trackSignerScanned()
            addAccount(pk, name)
        }.launchIn(lifecycleScope)
    }

    override fun onCameraStarted() {
        pairingSession?.cameraStarted()
    }

    override fun onCameraPermissionDenied() {
        pairingSession?.setBlocked(WalletImportPairingSession.Blocker.CameraPermissionDenied)
    }

    override fun onDestroy() {
        if (!isHandedOff && activity?.isChangingConfigurations != true) {
            pairingSession?.exit()
        }
        super.onDestroy()
    }

    private suspend fun trackImportError(errorType: String, errorMessage: String? = null) {
        importAnalytics.trackError(
            walletSource = WalletFlowWalletSource.Signer,
            errorType = errorType,
            errorMessage = errorMessage,
        )
    }

    private fun addAccount(publicKey: PublicKeyEd25519, name: String) {
        val fragment = InitScreen.newInstance(
            type = InitArgs.Type.SignerQR,
            publicKeyEd25519 = publicKey,
            name = name,
            importStartedTracked = true,
        )
        isHandedOff = true
        navigation?.add(fragment)
        finish()
    }

    companion object {
        fun newInstance() = SignerAddScreen()
    }
}
