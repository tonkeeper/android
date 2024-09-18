package com.tonapps.tonkeeper.ui.screen.signer.qr

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.tonapps.qr.QRImageAnalyzer
import com.tonapps.qr.ui.QRView
import com.tonapps.tonkeeper.core.signer.SignerApp
import com.tonapps.tonkeeperx.R
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.ton.api.pub.PublicKeyEd25519
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import uikit.base.BaseFragment
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.round
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SignerQRScreen: BaseFragment(R.layout.fragment_signer_qr), BaseFragment.BottomSheet {

    val contract = object : ResultContract<Uri, BitString> {
        override fun createResult(result: Uri) = Bundle().apply {
            putString(KEY_URI, result.toString())
        }

        override fun parseResult(bundle: Bundle): BitString {
            val uri = bundle.getString(KEY_URI)?.toUri()
            val sign = uri?.getQueryParameter("sign")
            if (sign.isNullOrBlank()) {
                throw CancellationException("SignerQRScreen canceled")
            }
            return BitString(sign)
        }
    }

    private val args: SignerQRArgs by lazy { SignerQRArgs(requireArguments()) }

    private val activityResultLauncher = registerForPermission { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        }
    }

    private val isReady = AtomicBoolean(false)
    private val qrAnalyzer = QRImageAnalyzer()
    private val chunks = mutableListOf<String>()
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var contentView: View
    private lateinit var qrView: QRView
    private lateinit var labelView: AppCompatTextView
    private lateinit var cameraContainerView: View
    private lateinit var cameraView: PreviewView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contentView = view.findViewById(R.id.content)
        contentView.background = QRBackground(requireContext())

        view.findViewById<View>(R.id.close).setOnClickListener { finish() }

        qrView = view.findViewById(R.id.qr)
        qrView.setContent(SignerApp.createSignUri(args.unsignedBody, args.publicKey))

        labelView = view.findViewById(R.id.label)
        labelView.text = args.label

        cameraContainerView = view.findViewById(R.id.camera_container)
        val cameraRadius = requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium)
        cameraContainerView.round(cameraRadius)

        cameraView = view.findViewById(R.id.camera)

        cameraExecutor = Executors.newSingleThreadExecutor()

        qrAnalyzer.flow.onEach(::handleBarcode).launchIn(lifecycleScope)
        checkAndStartCamera()
    }

    private fun handleBarcode(barcode: Barcode) {
        if (isReady.get()) return

        val data = barcode.rawValue ?: return

        if (data.startsWith("tonkeeper://")) {
            if (chunks.size > 0) {
                chunks.clear()
            }
            chunks.add(data)
        } else if (chunks.size > 0 && !data.startsWith("tonkeeper://")) {
            chunks.add(data)
        }

        val uri = try {
            chunks.joinToString("").toUri()
        } catch (ignored: Throwable) {
            null
        } ?: return

        if (isReady.compareAndSet(false, true)) {
            setResult(contract.createResult(uri))
        }
    }

    private fun checkAndStartCamera() {
        if (hasPermission(android.Manifest.permission.CAMERA)) {
            startCamera()
        } else {
            activityResultLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val context = requireContext()
        val cameraController = LifecycleCameraController(context)
        cameraController.setImageAnalysisAnalyzer(mainExecutor, qrAnalyzer)

        cameraController.bindToLifecycle(this)
        cameraView.controller = cameraController
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        qrAnalyzer.release()
    }

    companion object {

        const val KEY_URI = "uri"

        fun newInstance(
            publicKey: PublicKeyEd25519,
            unsignedBody: Cell,
            label: String = ""
        ): SignerQRScreen {
            val fragment = SignerQRScreen()
            fragment.setArgs(SignerQRArgs(publicKey, unsignedBody, label))
            return fragment
        }
    }
}