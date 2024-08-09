package com.tonapps.blockchain.ton

import android.os.Build
import java.security.SecureRandom
import kotlin.random.Random

object AndroidSecureRandom : Random() {

    private val secureRandom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        SecureRandom.getInstanceStrong()
    } else {
        SecureRandom()
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