package com.tonapps.tonkeeper.ui.screen.external.qr.keystone.sign

import android.os.Bundle
import android.view.View
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import com.tonapps.qr.ui.QRView
import com.tonapps.tonkeeper.ui.screen.external.qr.QRSignScreen
import com.tonapps.tonkeeper.ui.screen.external.qr.urFlow
import com.tonapps.tonkeeperx.R
import com.tonapps.ur.UREncoder
import com.tonapps.ur.registry.TonSignature
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.ton.bitstring.BitString
import org.ton.crypto.hex
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.gone
import uikit.extensions.round
import uikit.extensions.roundBottom
import uikit.extensions.setChildText
import uikit.extensions.setOnClickListener
import java.util.UUID
import java.util.concurrent.CancellationException

class KeystoneSignScreen: QRSignScreen() {

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
    private val _qrChunksFlow = MutableStateFlow<List<String>>(emptyList())
    private val qrChunksFlow = _qrChunksFlow.asStateFlow().filter { it.isNotEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            val encoder = UREncoder(args.ur, 80, 10, 0)
            val chunks = mutableListOf<String>()
            for (i in 0 until encoder.seqLen) {
                chunks.add(encoder.nextPart())
            }
            _qrChunksFlow.value = chunks.toList()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setChildText(R.id.step_1, Localization.keystone_sign_step1)
        view.setChildText(R.id.step_2, Localization.keystone_sign_step2)
        view.setChildText(R.id.step_3, Localization.keystone_sign_step3)

        val qrView = view.findViewById<QRView>(R.id.qr)

        collectFlow(urFlow<TonSignature>(), ::setResult)
        collectFlow(qrChunksFlow, qrView::setContent)
    }

    private fun setResult(tonSignature: TonSignature) {
        if (args.requestId.toByteArray().contentEquals(tonSignature.requestId)) {
            setResult(contract.createResult(tonSignature.signature))
        }
    }

    companion object {

        fun newInstance(
            requestId: String = UUID.randomUUID().toString(),
            unsignedBody: String,
            isTransaction: Boolean,
            address: String,
            keystone: WalletEntity.Keystone,
        ): KeystoneSignScreen {
            val fragment = KeystoneSignScreen()
            fragment.setArgs(KeystoneSignArgs(
                requestId = requestId,
                unsignedBody = unsignedBody,
                isTransaction = isTransaction,
                address = address,
                keystone = keystone
            ))
            return fragment
        }
    }
}