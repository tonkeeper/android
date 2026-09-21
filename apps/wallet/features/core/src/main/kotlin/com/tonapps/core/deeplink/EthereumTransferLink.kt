package com.tonapps.core.deeplink

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import java.math.BigInteger
import java.net.URLDecoder
import java.util.Locale

private const val PAY_PREFIX = "pay-"
private const val HEX_ADDRESS_LENGTH = 42
private const val TRANSFER_FUNCTION = "transfer"
private const val VALUE_PARAMETER = "value"
private const val ADDRESS_PARAMETER = "address"
private const val UINT256_PARAMETER = "uint256"

private const val MAX_EXPONENT = 78
private val UINT256_MAX = BigInteger.ONE.shiftLeft(256) - BigInteger.ONE

object EthereumTransferLink {

    private val schemes = listOf("ethereum:", "eth:")

    fun parse(value: String): DeepLinkRoute.EvmTransfer? {
        val payload = payload(value.trim()) ?: return null

        val (head, query) = payload.splitFirst('?')
        val (targetAndChainId, function) = head.splitFirst('/')
        val (rawTarget, rawChainId) = targetAndChainId.splitFirst('@')

        val target = hexAddress(rawTarget) ?: return null
        val chain = rawChainId?.let { evmChain(it) ?: return null }
        val parameters = parameters(query)

        return when (function?.lowercase(Locale.US)) {
            null, "" -> {
                val amount = parameters[VALUE_PARAMETER]?.let { atomicUnits(it) ?: return null }
                DeepLinkRoute.EvmTransfer(
                    recipient = target,
                    contract = null,
                    chain = chain,
                    amount = amount,
                )
            }
            TRANSFER_FUNCTION -> {
                val recipient = hexAddress(parameters[ADDRESS_PARAMETER]) ?: return null
                val amount = parameters[UINT256_PARAMETER]?.let { atomicUnits(it) ?: return null }
                DeepLinkRoute.EvmTransfer(
                    recipient = recipient,
                    contract = target,
                    chain = chain,
                    amount = amount,
                )
            }
            else -> null
        }
    }

    private fun payload(value: String): String? {
        val scheme = schemes.firstOrNull { value.startsWith(it, ignoreCase = true) } ?: return null
        val body = value.substring(scheme.length)
        if (body.startsWith(PAY_PREFIX, ignoreCase = true)) {
            return body.substring(PAY_PREFIX.length)
        }
        return body.takeIf { it.startsWith("0x", ignoreCase = true) }
    }

    private fun String.splitFirst(separator: Char): Pair<String, String?> {
        val index = indexOf(separator)
        if (index == -1) {
            return this to null
        }
        return substring(0, index) to substring(index + 1)
    }

    private fun hexAddress(value: String?): String? {
        if (value == null || value.length != HEX_ADDRESS_LENGTH || !value.startsWith("0x", ignoreCase = true)) {
            return null
        }
        return value.takeIf { address -> address.drop(2).all { it.isHexDigit() } }
    }

    private fun Char.isHexDigit(): Boolean {
        return this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }

    private fun evmChain(rawChainId: String): Chain.Evm? {
        if (rawChainId.isEmpty() || !rawChainId.all { it in '0'..'9' }) {
            return null
        }
        val chainId = rawChainId.trimStart('0').ifEmpty { "0" }
        return (Chain.fromCaip2("eip155:$chainId") as? Chain.Evm)
            ?.takeIf { it.network.mode == Network.Mode.Mainnet }
    }

    private fun parameters(query: String?): Map<String, String> {
        if (query == null) {
            return emptyMap()
        }
        val parameters = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            val (name, value) = pair.splitFirst('=')
            if (name.isEmpty() || value == null || parameters.containsKey(name)) {
                continue
            }
            parameters[name] = percentDecode(value)
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

    private fun atomicUnits(raw: String): BigInteger? {
        var mantissa = raw
        var exponent = 0

        val exponentIndex = mantissa.indexOfFirst { it == 'e' || it == 'E' }
        if (exponentIndex != -1) {
            val rawExponent = mantissa.substring(exponentIndex + 1).removePrefix("+")
            mantissa = mantissa.substring(0, exponentIndex)
            if (rawExponent.isNotEmpty()) {
                if (!rawExponent.all { it in '0'..'9' }) {
                    return null
                }
                exponent = rawExponent.toIntOrNull() ?: return null
            }
        }

        mantissa = mantissa.removePrefix("+")

        var fractionDigits = 0
        val dotIndex = mantissa.indexOf('.')
        if (dotIndex != -1) {
            val fraction = mantissa.substring(dotIndex + 1)
            fractionDigits = fraction.length
            mantissa = mantissa.substring(0, dotIndex) + fraction
        }

        val scale = exponent - fractionDigits
        if (mantissa.isEmpty() || !mantissa.all { it in '0'..'9' } || scale < 0 || scale > MAX_EXPONENT) {
            return null
        }
        return BigInteger(mantissa).multiply(BigInteger.TEN.pow(scale)).takeIf { it <= UINT256_MAX }
    }
}
