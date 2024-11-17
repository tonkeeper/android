package com.tonapps.tonkeeper.ui.screen.camera

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.camera.view.PreviewView
import androidx.core.net.toUri
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.toUriOrNull
import com.tonapps.tonkeeper.deeplink.DeepLink
import com.tonapps.tonkeeper.deeplink.DeepLinkRoute
import com.tonapps.tonkeeper.ui.base.QRCameraScreen
import com.tonapps.tonkeeper.ui.component.CameraFlashIconView
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.constantWhiteColor
import com.tonapps.uikit.color.stateList
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.withAlpha

class CameraScreen: QRCameraScreen(R.layout.fragment_camera), BaseFragment.BottomSheet {

    private val mode: CameraMode by lazy { requireArguments().getParcelableCompat(ARG_MODE)!! }

    private val rootViewModel: RootViewModel by activityViewModel()

    override lateinit var cameraView: PreviewView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val constantWhiteColor = requireContext().constantWhiteColor.withAlpha(.08f).stateList

        val closeView = view.findViewById<View>(R.id.close)
        closeView.setOnClickListener { finish() }
        closeView.backgroundTintList = constantWhiteColor

        cameraView = view.findViewById(R.id.camera)

        val flashView = view.findViewById<CameraFlashIconView>(R.id.flash)
        flashView.setOnClickListener { toggleFlash() }

        collectFlow(flashConfigFlow) { flashConfig ->
            if (!flashConfig.isFlashAvailable) {
                flashView.visibility = View.GONE
            } else {
                flashView.setFlashState(flashConfig.isFlashEnabled)
            }
        }

        collectFlow(readerFlow.map(::createUri).filterNotNull(), ::handleUri)
    }

    private fun handleUri(uri: Uri) {
        if (mode == CameraMode.Default) {
            rootViewModel.processDeepLink(uri, true, null, false, null)
            finish()
            return
        }

        val deeplink = DeepLink(DeepLink.fixBadUri(uri), true, null)
        val route = deeplink.route
        if (mode == CameraMode.Address && route is DeepLinkRoute.Transfer) {
            rootViewModel.processTransferDeepLink(route)
            finish()
        } else if (mode == CameraMode.TonConnect && route is DeepLinkRoute.TonConnect) {
            rootViewModel.processTonConnectDeepLink(deeplink, fromPackageName = null)
            finish()
        } else if (mode == CameraMode.Signer && route is DeepLinkRoute.Signer) {
            rootViewModel.processSignerDeepLink(route, true)
            finish()
        }
    }

    private fun createUri(value: String): Uri? {
        return value.toUriOrNull() ?: createTransferUri(value)
    }

    private fun createTransferUri(value: String): Uri? {
        if (value.isValidTonAddress()) {
            return "tonkeeper://transfer/$value".toUri()
        }
        return null
    }

    companion object {

        private const val ARG_MODE = "mode"

        fun newInstance(mode: CameraMode = CameraMode.Default): CameraScreen {
            val fragment = CameraScreen()
            fragment.putParcelableArg(ARG_MODE, mode)
            return fragment
        }
    }
}