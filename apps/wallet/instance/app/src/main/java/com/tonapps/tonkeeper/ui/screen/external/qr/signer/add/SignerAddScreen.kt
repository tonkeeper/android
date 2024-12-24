package com.tonapps.tonkeeper.ui.screen.external.qr.signer.add

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.core.signer.SignerApp
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.base.QRCameraScreen
import com.tonapps.tonkeeper.ui.component.CameraFlashIconView
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.constantWhiteColor
import com.tonapps.uikit.color.stateList
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.ton.api.pub.PublicKeyEd25519
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.pinToBottomInsets
import uikit.extensions.withAlpha
import uikit.navigation.Navigation.Companion.navigation

class SignerAddScreen: QRCameraScreen(R.layout.fragment_signer_add), BaseFragment.BottomSheet {

    override val fragmentName: String = "SignerAddScreen"

    override lateinit var cameraView: PreviewView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val constantWhiteColor = requireContext().constantWhiteColor.withAlpha(.08f).stateList

        val closeView = view.findViewById<View>(R.id.close)
        closeView.setOnClickListener { finish() }
        closeView.backgroundTintList = constantWhiteColor

        val aboutView = view.findViewById<View>(R.id.about)
        aboutView.backgroundTintList = constantWhiteColor
        aboutView.setOnClickListener {
            BrowserHelper.open(requireActivity(), "https://tonkeeper.com/signer")
        }

        cameraView = view.findViewById(R.id.camera)

        val flashView = view.findViewById<CameraFlashIconView>(R.id.flash)
        flashView.setOnClickListener { toggleFlash() }

        val signerOpenButton = view.findViewById<View>(R.id.signer_open)
        signerOpenButton.pinToBottomInsets()
        signerOpenButton.setOnClickListener {
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
            navigation?.toast(Localization.unknown_error)
        }.onEach { uri ->
            val pk = uri.getQueryParameter("pk")?.publicKeyFromHex() ?: return@onEach
            val name = uri.getQueryParameter("name") ?: ""
            addAccount(pk, name)
        }.launchIn(lifecycleScope)
    }

    private fun addAccount(publicKey: PublicKeyEd25519, name: String) {
        val fragment = InitScreen.newInstance(InitArgs.Type.SignerQR, publicKey, name)
        navigation?.add(fragment)
        finish()
    }

    companion object {
        fun newInstance() = SignerAddScreen()
    }
}