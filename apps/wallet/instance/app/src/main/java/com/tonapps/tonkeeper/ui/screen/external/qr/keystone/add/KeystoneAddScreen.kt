package com.tonapps.tonkeeper.ui.screen.external.qr.keystone.add

import android.os.Bundle
import android.view.View
import androidx.camera.view.PreviewView
import com.tonapps.tonkeeper.ui.base.QRCameraScreen
import com.tonapps.tonkeeper.ui.component.CameraFlashIconView
import com.tonapps.tonkeeper.ui.screen.external.qr.urFlow
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.constantWhiteColor
import com.tonapps.uikit.color.stateList
import com.tonapps.ur.registry.CryptoHDKey
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.coroutines.flow.map
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.hex
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.withAlpha
import uikit.navigation.Navigation.Companion.navigation

class KeystoneAddScreen: QRCameraScreen(R.layout.fragment_add_keystone), BaseFragment.BottomSheet {

    private data class KeystoneData(
        val publicKey: PublicKeyEd25519,
        val xfp: String,
        val path: String,
        val name: String?,
    )

    override lateinit var cameraView: PreviewView

    private lateinit var flashView: CameraFlashIconView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val constantWhiteColor = requireContext().constantWhiteColor.withAlpha(.08f).stateList

        val closeView = view.findViewById<View>(R.id.close)
        closeView.setOnClickListener { finish() }
        closeView.backgroundTintList = constantWhiteColor

        val aboutView = view.findViewById<View>(R.id.about)
        aboutView.backgroundTintList = constantWhiteColor
        aboutView.setOnClickListener {
            navigation?.openURL("https://keyst.one/")
            finish()
        }

        cameraView = view.findViewById(R.id.camera)

        flashView = view.findViewById(R.id.flash)
        flashView.setOnClickListener { toggleFlash() }

        collectFlow(urFlow<CryptoHDKey>().map {
            val name = if (it.name.isNullOrBlank()) {
                it.note
            } else {
                it.name
            }

            KeystoneData(
                publicKey = PublicKeyEd25519(it.key),
                xfp = hex(it.origin.sourceFingerprint),
                path = "m/" + it.origin.path,
                name = name
            )
        }, ::addAccount)

        collectFlow(flashConfigFlow) { flashConfig ->
            if (!flashConfig.isFlashAvailable) {
                flashView.visibility = View.GONE
            } else {
                flashView.setFlashState(flashConfig.isFlashEnabled)
            }
        }
    }

    private fun addAccount(data: KeystoneData) {
        navigation?.add(InitScreen.newInstance(
            type = InitArgs.Type.Keystone,
            publicKeyEd25519 = data.publicKey,
            name = data.name,
            keystone = WalletEntity.Keystone(data.xfp, data.path)
        ))
        finish()
    }

    companion object {
        fun newInstance() = KeystoneAddScreen()
    }
}