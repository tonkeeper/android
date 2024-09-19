package com.tonapps.tonkeeper.ui.screen.external.qr.signer.sign

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import com.tonapps.qr.ui.QRView
import com.tonapps.tonkeeper.core.signer.SignerApp
import com.tonapps.tonkeeper.ui.screen.external.qr.QRSignScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import org.ton.api.pub.PublicKeyEd25519
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import uikit.base.BaseDrawable
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.extensions.getDimension
import uikit.extensions.setBackground
import uikit.extensions.setBackgroundColor
import uikit.extensions.setChildText
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class SignerSignScreen: QRSignScreen() {

    val contract = object : ResultContract<Uri, BitString> {

        private val KEY_URI = "uri"

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

    private val args: SignerSignArgs by lazy { SignerSignArgs(requireArguments()) }

    private val isReady = AtomicBoolean(false)
    private val chunks = mutableListOf<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val orangeColor = getColor(R.color.constantQROrange)

        view.setChildText(R.id.step_1, Localization.signer_step_1)
        view.setChildText(R.id.step_2, Localization.signer_step_2)
        view.setChildText(R.id.step_3, Localization.signer_step_3)
        view.setBackgroundColor(R.id.content, orangeColor)

        val qrView = view.findViewById<QRView>(R.id.qr)
        qrView.color = orangeColor
        qrView.setContent(SignerApp.createSignUri(args.unsignedBody, args.publicKey))

        collectFlow(readerFlow, ::readQRCode)
    }

    private fun readQRCode(value: String) {
        if (isReady.get()) return

        if (value.startsWith("tonkeeper://")) {
            if (chunks.size > 0) {
                chunks.clear()
            }
            chunks.add(value)
        } else if (chunks.size > 0 && !value.startsWith("tonkeeper://")) {
            chunks.add(value)
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

    companion object {

        fun newInstance(
            publicKey: PublicKeyEd25519,
            unsignedBody: Cell,
            label: String = ""
        ): SignerSignScreen {
            val fragment = SignerSignScreen()
            fragment.setArgs(SignerSignArgs(publicKey, unsignedBody, label))
            return fragment
        }

        private class QRDrawable(context: Context): BaseDrawable() {

            private val hookSize = 42f.dp
            private val radius = context.getDimension(uikit.R.dimen.cornerMedium)
            private val color = context.getColor(R.color.constantQROrange)
            private val path = Path()
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            init {
                paint.color = color
                paint.style = Paint.Style.FILL
                paint.pathEffect = CornerPathEffect(radius)
            }

            override fun draw(canvas: Canvas) {
                canvas.drawPath(path, paint)
            }

            override fun onBoundsChange(bounds: Rect) {
                super.onBoundsChange(bounds)
                val rect = RectF(bounds)

                path.reset()
                path.moveTo(rect.left, rect.top)
                path.lineTo(rect.right, rect.top)
                path.lineTo(rect.right, rect.bottom - hookSize)
                path.lineTo(rect.right - hookSize, rect.bottom)
                path.lineTo(rect.left, rect.bottom)
                path.close()
            }

        }

    }
}