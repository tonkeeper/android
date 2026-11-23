package com.tonapps.tonkeeper.ui.screen.camera

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
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
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.Scanner.ScannerErrorType
import com.tonapps.bus.generated.Events.Scanner.ScannerInput
import com.tonapps.bus.scanner.ScanAnalytics
import com.tonapps.core.deeplink.DeepLink
import com.tonapps.core.deeplink.DeepLinkBuilder
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.toUriOrNull
import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.extensions.toast
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
import uikit.extensions.setMoonOnClickListener

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
    private val entryUtm: UtmParameters by lazy {
        requireArguments().getParcelableCompat(ARG_UTM) ?: UtmParameters.Empty
    }
    private val scanAnalytics = ScanAnalytics.default()

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { readQRCodeFromImage(it) }
        }

    private val rootViewModel: RootViewModel by activityViewModel()

    override lateinit var cameraView: PreviewView
    private lateinit var galleryButton: Button

    private var lastScanValue: String? = null
    private var lastScanAt = 0L
    private var lastErrorAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanAnalytics.open(entryUtm)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val constantWhiteColor = requireContext().constantWhiteColor.withAlpha(.08f).stateList

        val closeView = view.findViewById<View>(R.id.close)
        closeView.setMoonOnClickListener { finish() }
        closeView.backgroundTintList = constantWhiteColor

        cameraView = view.findViewById(R.id.camera)

        val flashView = view.findViewById<CameraFlashIconView>(R.id.flash)
        flashView.setMoonOnClickListener { toggleFlash() }

        galleryButton = view.findViewById(R.id.gallery)
        galleryButton.setMoonOnClickListener { pickImage() }
        galleryButton.pinToBottomInsets()

        collectFlow(flashConfigFlow) { flashConfig ->
            if (!flashConfig.isFlashAvailable) {
                flashView.visibility = View.GONE
            } else {
                flashView.setFlashState(flashConfig.isFlashEnabled)
            }
        }

        collectFlow(scanFlow, ::handleScan)
    }

    private fun handleScan(value: String) {
        val now = SystemClock.uptimeMillis()
        if (value == lastScanValue && now - lastScanAt < REPEAT_SCAN_DELAY) {
            return
        }
        lastScanValue = value
        lastScanAt = now
        handleValue(value, ScannerInput.Camera)
    }

    private fun handleValue(value: String, input: ScannerInput) {
        val uri = createUri(value)
        if (uri == null) {
            showInvalidLink()
            scanAnalytics.error(value, input, ScannerErrorType.InvalidLink)
        } else {
            handleUri(uri, value, input)
        }
    }

    private fun showInvalidLink() {
        val now = SystemClock.uptimeMillis()
        if (now - lastErrorAt < REPEAT_SCAN_DELAY) {
            return
        }
        lastErrorAt = now
        navigation?.toast(Localization.invalid_link)
    }

    private fun handleUri(uri: Uri, code: String, input: ScannerInput) {
        val deeplink = DeepLink(DeepLink.fixBadUri(uri), true, null)
        val route = deeplink.route
        if (mode == CameraMode.Default) {
            val opened = rootViewModel.processDeepLink(uri, true, null, false, null)
            reportOutcome(code, input, errorTypeFor(route) ?: ScannerErrorType.InvalidLink.takeUnless { opened })
            finish()
            return
        }

        reportOutcome(code, input, errorTypeFor(route))
        if (mode == CameraMode.Address && route is DeepLinkRoute.Transfer) {
            rootViewModel.processTransferDeepLink(deeplink, route)
            finish()
        } else if (mode == CameraMode.TonConnect && route is DeepLinkRoute.TonConnect) {
            rootViewModel.processTonConnectDeepLink(deeplink, fromPackageName = null)
            finish()
        } else if (mode == CameraMode.Signer && route is DeepLinkRoute.Signer) {
            rootViewModel.processSignerDeepLink(route, true)
            finish()
        } else {
            showInvalidLink()
        }
    }

    // The legacy scanner hands links over with internal = false, so an Internal route closes it without opening anything.
    private fun errorTypeFor(route: DeepLinkRoute): ScannerErrorType? {
        val accepted = when (mode) {
            CameraMode.Default -> route !is DeepLinkRoute.Internal
            CameraMode.Address -> route is DeepLinkRoute.Transfer
            CameraMode.TonConnect -> route is DeepLinkRoute.TonConnect
            CameraMode.Signer -> route is DeepLinkRoute.Signer
        }
        return when {
            route is DeepLinkRoute.Unknown -> ScannerErrorType.InvalidLink
            !accepted -> ScannerErrorType.UnsupportedHere
            else -> null
        }
    }

    private fun reportOutcome(code: String, input: ScannerInput, errorType: ScannerErrorType?) {
        if (errorType == null) {
            scanAnalytics.success(code, input)
        } else {
            scanAnalytics.error(code, input, errorType)
        }
    }

    private fun readQRCodeFromImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.Main) {
            val value = readQRCode(uri)
            // A gallery pick always answers; only repeated camera scans are throttled.
            lastErrorAt = 0L
            if (value == null) {
                showInvalidLink()
                scanAnalytics.noCode(ScannerInput.Gallery)
            } else {
                handleValue(value, ScannerInput.Gallery)
            }
        }
    }

    private suspend fun readQRCode(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromFilePath(requireContext(), uri)
            val task = barcodeScanner.process(inputImage).await()
            task.firstOrNull()?.rawValue
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
        val plainAddress = DeepLinkBuilder.dropAmountOnlyQuery(value) ?: value
        return when {
            chains.contains(Blockchain.TON) && plainAddress.isValidTonAddress() -> {
                "tonkeeper://transfer/$plainAddress".toUri()
            }
            chains.contains(Blockchain.TRON) && plainAddress.isValidTronAddress() -> {
                "tonkeeper://transfer/$plainAddress".toUri()
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
        private const val ARG_UTM = "utm"

        // Outlasts the error toast: ToastView hides a toast when the same one is shown again.
        private const val REPEAT_SCAN_DELAY = 3000L

        fun newInstance(
            mode: CameraMode = CameraMode.Default,
            chains: List<Blockchain> = listOf(Blockchain.TON),
            utm: UtmParameters = UtmParameters.Empty,
        ): CameraScreen {
            val fragment = CameraScreen()
            val bundle = Bundle().apply {
                putParcelable(ARG_MODE, mode)
                putStringArrayList(ARG_CHAINS, ArrayList(chains.map { it.name }))
                putParcelable(ARG_UTM, utm)
            }
            fragment.setArgs(bundle)
            return fragment
        }
    }
}