package com.tonapps.tonkeeper.ui.screen.camera

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.mlkit.vision.common.InputImage
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.toUriOrNull
import com.tonapps.tonkeeper.deeplink.DeepLink
import com.tonapps.tonkeeper.deeplink.DeepLinkRoute
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.extensions.toastLoading
import com.tonapps.tonkeeper.ui.base.QRCameraScreen
import com.tonapps.tonkeeper.ui.component.CameraFlashIconView
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.constantWhiteColor
import com.tonapps.uikit.color.stateList
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.pinToBottomInsets
import uikit.extensions.withAlpha
import uikit.navigation.Navigation.Companion.navigation

class CameraScreen: QRCameraScreen(R.layout.fragment_camera), BaseFragment.BottomSheet {

    override val fragmentName: String = "CameraScreen"

    private val mode: CameraMode by lazy { requireArguments().getParcelableCompat(ARG_MODE)!! }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { readQRCodeFromImage(it) }
    }

    private val rootViewModel: RootViewModel by activityViewModel()

    override lateinit var cameraView: PreviewView
    private lateinit var galleryButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val constantWhiteColor = requireContext().constantWhiteColor.withAlpha(.08f).stateList

        val closeView = view.findViewById<View>(R.id.close)
        closeView.setOnClickListener { finish() }
        closeView.backgroundTintList = constantWhiteColor

        cameraView = view.findViewById(R.id.camera)

        val flashView = view.findViewById<CameraFlashIconView>(R.id.flash)
        flashView.setOnClickListener { toggleFlash() }

        galleryButton = view.findViewById(R.id.gallery)
        galleryButton.setOnClickListener { pickImage() }
        galleryButton.pinToBottomInsets()

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

    private fun readQRCodeFromImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.Main) {
            val outputUri = readQRCode(uri)
            if (outputUri == null) {
                navigation?.toast(Localization.invalid_link)
            } else {
                handleUri(outputUri)
            }
        }
    }

    private suspend fun readQRCode(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromFilePath(requireContext(), uri)
            val task = barcodeScanner.process(inputImage).await()
            val rawValue = task.firstOrNull()?.rawValue ?: throw IllegalStateException("No barcode found")
            createUri(rawValue)
        } catch (e: Throwable) {
            null
        }
    }

    private fun pickImage() {
        try {
            imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
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