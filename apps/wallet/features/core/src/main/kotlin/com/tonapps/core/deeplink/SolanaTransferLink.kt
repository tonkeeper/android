package com.tonapps.core.deeplink

import java.math.BigDecimal
import java.math.BigInteger
import java.net.URLDecoder

private const val SCHEME = "solana:"
private const val AMOUNT_PARAMETER = "amount"
private const val SPL_TOKEN_PARAMETER = "spl-token"
private const val REFERENCE_PARAMETER = "reference"
private const val MEMO_PARAMETER = "memo"

private const val PUBLIC_KEY_SIZE = 32
private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
private val BASE58_RADIX = BigInteger.valueOf(58)
private val AMOUNT_PATTERN = Regex("""\d+(\.\d*)?""")

/**
 * Solana Pay transfer request: `solana:<recipient>?amount=<SOL>&spl-token=<mint>`.
 * https://docs.solanapay.com/spec#transfer-request
 *
 * A `reference` or a `memo` becomes part of the transaction the link asks for, and the Send
 * screen can carry neither, so such a link is rejected instead of being paid without them.
 * `label` and `message` are only for display. A transaction request (`solana:https://…`) names
 * an endpoint, not a recipient. Matches the iOS parser.
 */
object SolanaTransferLink {

    fun parse(value: String): DeepLinkRoute.SolanaTransfer? {
        val trimmed = value.trim()
        if (!trimmed.startsWith(SCHEME, ignoreCase = true)) {
            return null
        }

        val (recipient, query) = trimmed.substring(SCHEME.length).splitFirst('?')
        if (!isPublicKey(recipient)) {
            return null
        }

        val parameters = parameters(query)
        if (parameters.containsKey(REFERENCE_PARAMETER) || !parameters[MEMO_PARAMETER].isNullOrEmpty()) {
            return null
        }

        val mint = parameters[SPL_TOKEN_PARAMETER]?.let { it.takeIf(::isPublicKey) ?: return null }
        val amount = parameters[AMOUNT_PARAMETER]?.let { it.takeIf(AMOUNT_PATTERN::matches) ?: return null }
        return DeepLinkRoute.SolanaTransfer(
            recipient = recipient,
            mint = mint,
            amount = amount?.let(::BigDecimal),
        )
    }

    private fun String.splitFirst(separator: Char): Pair<String, String?> {
        val index = indexOf(separator)
        if (index == -1) {
            return this to null
        }
        return substring(0, index) to substring(index + 1)
    }

    private fun parameters(query: String?): Map<String, String> {
        if (query == null) {
            return emptyMap()
        }
        val parameters = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            val (name, value) = pair.splitFirst('=')
            // A parameter without a value still counts: a bare `reference` must reject the link too.
            if (name.isEmpty() || parameters.containsKey(name)) {
                continue
            }
            parameters[name] = percentDecode(value.orEmpty())
        }
        return parameters
    }

    private fun percentDecode(value: String): String {
        return try {
            URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            value
        }
    }

    /** An ed25519 public key or a program-derived address: 32 bytes in base58. */
    private fun isPublicKey(value: String): Boolean {
        if (value.isEmpty() || value.any { it !in BASE58_ALPHABET }) {
            return false
        }

        // Each leading '1' is a zero byte the number itself does not carry.
        val leadingZeros = value.takeWhile { it == '1' }.length
        val number = value.fold(BigInteger.ZERO) { acc, char ->
            acc.multiply(BASE58_RADIX).add(BigInteger.valueOf(BASE58_ALPHABET.indexOf(char).toLong()))
        }
        val size = (number.bitLength() + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS
        return leadingZeros + size == PUBLIC_KEY_SIZE
    }
}
