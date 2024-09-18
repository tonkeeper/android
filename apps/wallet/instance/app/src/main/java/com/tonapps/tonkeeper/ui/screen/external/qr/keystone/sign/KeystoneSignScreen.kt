package com.tonapps.tonkeeper.ui.screen.external.qr.keystone.sign

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.camera.view.PreviewView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.tonapps.qr.ui.QRView
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.base.QRCameraScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.ur.ResultType
import com.tonapps.ur.URDecoder
import com.tonapps.ur.UREncoder
import com.tonapps.ur.registry.CryptoKeypath
import com.tonapps.ur.registry.TonSignRequest
import com.tonapps.ur.registry.TonSignature
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.bitstring.BitString
import org.ton.crypto.hex
import uikit.base.BaseFragment
import uikit.extensions.gone
import uikit.extensions.setChildText
import uikit.navigation.Navigation.Companion.navigation
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class KeystoneSignScreen: QRCameraScreen(R.layout.fragment_external_qr_sign), BaseFragment.BottomSheet {

    val contract = object : ResultContract<ByteArray, BitString> {

        private val KEY_SIGNATURE = "signature"

        override fun createResult(result: ByteArray) = Bundle().apply {
            putString(KEY_SIGNATURE, hex(result))
        }

        override fun parseResult(bundle: Bundle): BitString {
            val hex = bundle.getString(KEY_SIGNATURE)
            if (hex.isNullOrBlank()) {
                throw CancellationException("KeystoneSignScreen canceled")
            }
            return BitString(hex)
        }
    }

    private val args: KeystoneSignArgs by lazy { KeystoneSignArgs(requireArguments()) }

    private val urDecoder = URDecoder()
    private val isReady = AtomicBoolean(false)

    private lateinit var qrView: QRView
    override lateinit var cameraView: PreviewView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setChildText(R.id.step_1, Localization.keystone_sign_step1)
        view.setChildText(R.id.step_2, Localization.keystone_sign_step2)
        view.setChildText(R.id.step_3, Localization.keystone_sign_step3)
        view.gone(R.id.transaction)
        view.gone(R.id.label)

        cameraView = view.findViewById(R.id.camera)
        qrView = view.findViewById(R.id.qr)
        displayQR()
        observeQRCode()
    }

    private fun displayQR() {
        lifecycleScope.launch(Dispatchers.IO) {
            val path = CryptoKeypath(args.path, hex(args.keystone.xfp))
            val tonSignRequest = TonSignRequest(null, hex(args.unsignedBody), if (args.isTransaction) 1 else 2, path, args.address, "Tonkeeper")
            val ur = tonSignRequest.toUR()
            val encoder = UREncoder(ur, 200, 10, 0)
            val chunks = mutableListOf<String>()
            for (i in 0 until encoder.seqLen) {
                chunks.add(encoder.nextPart())
            }
            withContext(Dispatchers.Main) {
                qrView.setContent(chunks.toList())
            }
        }
    }

    private fun observeQRCode() {
        readerFlow.map { urDecoder.receivePart(fixReceivedPart(it)) }
            .filter { it }
            .map { urDecoder.result }
            .filter { it.type == ResultType.SUCCESS }
            .map { it.ur.decodeFromRegistry() as TonSignature }
            .flowOn(Dispatchers.IO)
            .catch { navigation?.toast(Localization.unknown_error) }
            .onEach(::result).launchIn(lifecycleScope)
    }

    private fun result(tonSignature: TonSignature) {
        if (isReady.compareAndSet(false, true)) {
            setResult(contract.createResult(tonSignature.signature))
        }
    }

    private fun fixReceivedPart(part: String): String {
        if (part.startsWith("http://", ignoreCase = true)) {
            return part.removePrefix("http://")
        } else if (part.startsWith("https://", ignoreCase = true)) {
            return part.removePrefix("https://")
        }
        return part
    }

    companion object {

        fun newInstance(
            unsignedBody: String,
            isTransaction: Boolean,
            address: String,
            keystone: WalletEntity.Keystone,
        ): KeystoneSignScreen {
            val fragment = KeystoneSignScreen()
            fragment.setArgs(KeystoneSignArgs(unsignedBody, isTransaction, address, keystone))
            return fragment
        }
    }
}