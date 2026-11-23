package com.tonapps.perps.funding

import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.perps.security.PerpsSecurityConfig
import com.tonapps.perps.security.tonAccountId
import com.tonapps.wallet.data.multichain.lighter.PerpetualDepositProvider
import com.tonapps.wallet.data.multichain.lighter.PerpsQuoteError
import kotlinx.coroutines.CancellationException
import java.math.BigInteger

private const val TON_PAYMENT = "ton payment"
private const val TEXT_COMMENT_OP_BYTES = 4
private const val BITS_PER_BYTE = 8
private const val ALLOWED_SEND_MODE_FLAGS = 0b11
private val ORDER_ID_COMMENT = Regex("0x[0-9a-fA-F]{64}")

internal object PerpsTonDepositReader {

    fun read(
        provider: PerpetualDepositProvider,
        payment: Operation,
        amountUnits: BigInteger,
        security: PerpsSecurityConfig?,
    ): BigInteger {
        if (provider != PerpetualDepositProvider.Relay) {
            refuse("provider")
        }
        val depositories = security?.tonRelayDepositories.orEmpty()
        if (depositories.isEmpty()) {
            refuse("relay depository")
        }
        val payload = payment.payloads.singleOrNull() ?: refuse("payloads")
        if (payload.type != Payload.Type.Main || payload.chain != Chain.Ton.Mainnet || payload.version != Payload.VERSION) {
            refuse("payload")
        }
        val message = payload.meta as? PayloadMeta.Ton.Main ?: refuse("payload")
        if (message.token != null || message.stateInit != null) {
            refuse("message")
        }
        if (tonAccountId(message.to.display) !in depositories) {
            refuse("destination")
        }
        val sent = message.value.toJava()
        if (!fitsDepositBudget(sent, amountUnits)) {
            refuse("value")
        }
        if (message.mode?.let { it and ALLOWED_SEND_MODE_FLAGS.inv() != 0 } == true) {
            refuse("mode")
        }
        val comment = comment(message) ?: refuse("body")
        if (!ORDER_ID_COMMENT.matches(comment)) {
            refuse("body")
        }
        return sent
    }

    private fun comment(message: PayloadMeta.Ton.Main): String? {
        val body = message.body
        val comment = message.comment?.takeIf { it.isNotEmpty() }
        return when {
            body != null && comment == null -> textComment(body)
            body == null -> comment
            else -> null
        }
    }

    private fun textComment(body: String): String? {
        val cell = try {
            body.cellFromBase64()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        val bits = cell.bits
        if (cell.refs.isNotEmpty() || bits.size % BITS_PER_BYTE != 0 || bits.size < TEXT_COMMENT_OP_BYTES * BITS_PER_BYTE) {
            return null
        }
        val bytes = bits.toByteArray()
        if (bytes.take(TEXT_COMMENT_OP_BYTES).any { it != 0.toByte() }) {
            return null
        }
        return bytes.copyOfRange(TEXT_COMMENT_OP_BYTES, bytes.size).toString(Charsets.UTF_8)
    }

    private fun refuse(field: String): Nothing {
        throw PerpsDepositException.QuoteRejected(PerpsQuoteError.UnexpectedOperation("$TON_PAYMENT $field"))
    }
}
