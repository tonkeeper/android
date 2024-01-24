package core.qr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull

class QRImageAnalyzer: ImageAnalysis.Analyzer {

    private val _flow = MutableStateFlow<Barcode?>(null)

    val flow = _flow.asStateFlow().filterNotNull().distinctUntilChangedBy {
        it.rawValue
    }

    private val barcodeScanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build())

    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image?.let { InputImage.fromMediaImage(it, imageProxy.imageInfo.rotationDegrees) } ?: return imageProxy.close()
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                handleBarcodes(barcodes)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleBarcodes(barcode: List<Barcode>) {
        for (b in barcode) {
            _flow.tryEmit(b)
        }
    }

    fun release() {
        barcodeScanner.close()
    }
}