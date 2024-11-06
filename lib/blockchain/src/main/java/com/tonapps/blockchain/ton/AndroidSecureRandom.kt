package com.tonapps.blockchain.ton

import android.annotation.SuppressLint
import android.os.Build
import android.os.SystemClock
import java.nio.ByteBuffer
import java.security.SecureRandom
import kotlin.experimental.xor
import kotlin.random.Random

object AndroidSecureRandom : Random() {

    private val secureRandom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        SecureRandom.getInstanceStrong()
    } else {
        SecureRandom()
    }

    @SuppressLint("SecureRandom")
    fun seed(data: ByteArray) {
        if (data.size > 32) {
            val additionalEntropy = ByteArray(data.size)
            var entropyIndex = 0
            for (byte in data) {
                additionalEntropy[entropyIndex] = (additionalEntropy[entropyIndex] xor byte)
                entropyIndex = (entropyIndex + 1) % additionalEntropy.size
            }
            secureRandom.setSeed(additionalEntropy)
        } else {
            secureRandom.setSeed(ByteBuffer.allocate(java.lang.Long.BYTES).putLong(SystemClock.elapsedRealtimeNanos()).array())
        }
    }

    override fun nextBits(bitCount: Int): Int = nextInt().ushr(32 - bitCount) and (-bitCount).shr(31)

    override fun nextInt(): Int = secureRandom.nextInt()

    override fun nextBytes(array: ByteArray, fromIndex: Int, toIndex: Int): ByteArray {
        val tmp = ByteArray(toIndex - fromIndex)
        secureRandom.nextBytes(tmp)
        tmp.copyInto(array, fromIndex)
        return array
    }
}