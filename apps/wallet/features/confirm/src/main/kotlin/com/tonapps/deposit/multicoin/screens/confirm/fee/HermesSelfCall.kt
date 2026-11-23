package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.chain.ethereum.utils.Hermes
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain

object HermesSelfCall {

    fun isSelfCall(sender: String, to: String): Boolean = to.equals(sender, ignoreCase = true)

    /** [calls] as the one call of the sender's to itself, which [unbatch] reads back as them. */
    fun batch(calls: List<Hermes.Call>): String {
        return Hermes.execute(Hermes.mode(), Hermes.encodeCalls(calls)).encodeHex()
    }

    fun unbatch(sender: String, value: BigInteger, data: String?, chain: Chain): List<Hermes.Call>? {
        if (value.isPositive || data.isNullOrBlank()) {
            return null
        }
        val calls = readExecute(data, chain) ?: return null
        if (calls.any { it.target.display.equals(sender, ignoreCase = true) }) {
            return null
        }
        return calls
    }

    private fun readExecute(data: String, chain: Chain): List<Hermes.Call>? {
        return try {
            val calldata = data.decodeHex()
            val executionData = SELECTOR_SIZE + calldata.uintAt(SELECTOR_SIZE + WORD) + WORD
            val array = executionData + calldata.uintAt(executionData)
            val heads = array + WORD
            val calls = List(calldata.uintAt(array)) { index ->
                val call = heads + calldata.uintAt(heads + index * WORD)
                val callData = call + calldata.uintAt(call + WORD * 2) + WORD
                Hermes.Call(
                    target = Address.force(calldata.copyOfRange(call + WORD - ADDRESS_SIZE, call + WORD).encodeHex(), chain),
                    value = BigInteger.parseString(calldata.copyOfRange(call + WORD, call + WORD * 2).toHexDigits(), HEX_RADIX),
                    data = calldata.copyOfRange(callData, callData + calldata.uintAt(callData - WORD)),
                )
            }
            calls.takeIf { it.isNotEmpty() && Hermes.execute(Hermes.mode(), Hermes.encodeCalls(it)).contentEquals(calldata) }
        } catch (e: IndexOutOfBoundsException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    private fun ByteArray.uintAt(offset: Int): Int {
        val value = BigInteger.parseString(copyOfRange(offset, offset + WORD).toHexDigits(), HEX_RADIX)
        require(value <= BigInteger.fromInt(size)) { "ABI offset $value is past the calldata's $size bytes" }
        return value.intValue()
    }

    private fun String.decodeHex(): ByteArray {
        val digits = removePrefix(HEX_PREFIX)
        require(digits.length % 2 == 0) { "Odd hex length" }
        return ByteArray(digits.length / 2) { index ->
            digits.substring(index * 2, index * 2 + 2).toInt(HEX_RADIX).toByte()
        }
    }

    private fun ByteArray.encodeHex(): String = HEX_PREFIX + toHexDigits()

    private fun ByteArray.toHexDigits(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private const val WORD = 32
private const val SELECTOR_SIZE = 4
private const val ADDRESS_SIZE = 20
private const val HEX_RADIX = 16
private const val HEX_PREFIX = "0x"
