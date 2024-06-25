package com.tonapps.ledger.ble

import android.util.Log
import com.ledger.live.ble.BleManager
import com.tonapps.ledger.transport.Transport
import io.ktor.util.hex
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

class BleTransport(private val bleManager: BleManager) : Transport {
    override suspend fun exchange(apdu: ByteArray): ByteArray {
        if (bleManager.isConnected) {
            return suspendCancellableCoroutine { continuation ->
                bleManager.send(apduHex = hex(apdu), onSuccess = { response ->
                    continuation.resume(hex(response)) {
                        Log.e("BleTransport", "Error resuming coroutine", it)
                    }
                }, onError = { error ->
                    continuation.resumeWithException(IllegalStateException(error))
                })
            }
        } else {
            throw IllegalStateException("BleTransport is not connected")
        }
    }
}