package com.tonapps.tonkeeper.ui.screen.camera

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.mlkit.vision.common.InputImage
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.blockchain.tron.isValidTronAddress
import com.tonapps.core.deeplink.DeepLink
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.toUriOrNull
import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.koin.analytics
import com.tonapps.tonkeeper.ui.base.QRCameraScreen
import com.tonapps.tonkeeper.ui.component.CameraFlashIconView
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.constantWhiteColor
import com.tonapps.uikit.color.stateList
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.QRScannerExtendsEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.pinToBottomInsets
import uikit.extensions.withAlpha
import uikit.navigation.Navigation.Companion.navigation

class CameraScreen : QRCameraScreen(R.layout.fragment_camera), BaseFragment.BottomSheet {

    override val fragmentName: String = "CameraScreen"

    private val accountRepository: AccountRepository by inject()
    private val api: API by inject()
    private val qrScannerExtends: List<QRScannerExtendsEntity>
        get() = api.getConfig(TonNetwork.MAINNET).qrScannerExtends.filter { it.version == 1 }

    private val mode: CameraMode by lazy { requireArguments().getParcelableCompat(ARG_MODE)!! }
    private val chains: List<Blockchain> by lazy {
       requireArguments().getStringArrayList(ARG_CHAINS)!!.map { Blockchain.valueOf(it) }
    }

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { readQRCodeFromImage(it) }
        }

    private val rootViewModel: RootViewModel by activityViewModel()

    override lateinit var cameraView: PreviewView
    private lateinit var galleryButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context?.analytics?.simpleTrackEvent("scan_open")
    }

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
            rootViewModel.processTransferDeepLink(deeplink, route)
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
            val rawValue =
                task.firstOrNull()?.rawValue ?: throw IllegalStateException("No barcode found")
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
        var uri = if (value.startsWith("tron:", ignoreCase = true)) {
            createTransferUri(value)
        } else {
            value.toUriOrNull() ?: createTransferUri(value)
        }
        if (uri == null) {
            uri = qrScannerExtends.firstNotNullOfOrNull {
                val url = it.buildUrl(value) ?: return@firstNotNullOfOrNull null
                "tonkeeper://dapp/$url".toUriOrNull()
            }
        }
        return uri
    }

    private fun createTransferUri(value: String): Uri? {
        return when {
            chains.contains(Blockchain.TON) && value.isValidTonAddress() -> {
                "tonkeeper://transfer/$value".toUri()
            }
            chains.contains(Blockchain.TRON) && value.isValidTronAddress() -> {
                "tonkeeper://transfer/$value".toUri()
            }
            chains.contains(Blockchain.TRON) && value.startsWith("tron:", ignoreCase = true) -> {
                val withoutScheme = value.removePrefix("tron:")
                val address = withoutScheme.substringBefore("?")
                val query = withoutScheme.substringAfter("?", "")
                var amountNano: String? = null
                if (query.isNotEmpty()) {
                    val params = query.split("&")
                    val amountStr = params.firstOrNull { it.startsWith("amount=") }?.substringAfter("=")
                    if (!amountStr.isNullOrEmpty()) {
                        amountNano = try {
                            Coins.of(amountStr).toNano(TokenEntity.TRON_USDT.decimals)
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                val builder = "tonkeeper://transfer/$address".toUri().buildUpon()
                builder.appendQueryParameter("jettonAddress", TokenEntity.TRON_USDT.address)
                if (amountNano != null) {
                    builder.appendQueryParameter("amount", amountNano)
                }
                builder.build()
            }
            else -> null
        }
    }

    companion object {

        private const val ARG_MODE = "mode"
        private const val ARG_CHAINS = "chains"

        fun newInstance(
            mode: CameraMode = CameraMode.Default,
            chains: List<Blockchain> = listOf(Blockchain.TON)
        ): CameraScreen {
            val fragment = CameraScreen()
            val bundle = Bundle().apply {
                putParcelable(ARG_MODE, mode)
                putStringArrayList(ARG_CHAINS, ArrayList(chains.map { it.name }))
            }
            fragment.setArgs(bundle)
            return fragment
        }
    }
}