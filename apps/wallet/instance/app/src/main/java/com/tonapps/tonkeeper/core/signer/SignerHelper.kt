package com.tonapps.tonkeeper.core.signer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.extensions.toIpcFriendly
import com.tonapps.tonkeeper.core.signer.SignerHiddenActivity.Companion.ARG_SIGN
import kotlinx.coroutines.suspendCancellableCoroutine
import org.ton.api.pub.PublicKeyEd25519
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import kotlin.coroutines.resume

object SignerHelper {

    suspend fun invoke(
        context: Context,
        publicKey: PublicKeyEd25519,
        body: Cell,
    ): BitString? = suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel()  }

        val resultReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {

            public override fun onReceiveResult(code: Int, bundle: Bundle) {
                if (cancellationSignal.isCanceled) {
                    return
                }
                val sign = bundle.getString(ARG_SIGN)?.let { BitString(it) }
                continuation.resume(sign)
            }
        }

        try {
            startHiddenActivity(context, publicKey, body, resultReceiver)
        } catch (e: Throwable) {
            if (!cancellationSignal.isCanceled) {
                continuation.resume(null)
            }
        }
    }

    private fun startHiddenActivity(
        context: Context,
        publicKey: PublicKeyEd25519,
        body: Cell,
        resultReceiver: ResultReceiver
    ) {
        val intent = Intent(context, SignerHiddenActivity::class.java)
        intent.putExtra(SignerHiddenActivity.ARG_PUBLIC_KEY, publicKey.hex())
        intent.putExtra(SignerHiddenActivity.ARG_BODY, body.hex())
        intent.putExtra(SignerHiddenActivity.ARG_RESULT_RECEIVER, resultReceiver.toIpcFriendly())
        intent.flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
        context.startActivity(intent)
    }

}