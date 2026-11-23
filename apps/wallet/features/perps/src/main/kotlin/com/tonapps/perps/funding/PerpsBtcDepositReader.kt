package com.tonapps.perps.funding

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.PsbtOutput
import com.tonapps.chainkit.core.op.inputs
import com.tonapps.chainkit.core.op.outputs
import com.tonapps.perps.security.PerpsSecurityConfig
import com.tonapps.perps.security.btcAddress
import com.tonapps.wallet.data.multichain.lighter.PerpetualDepositProvider
import com.tonapps.wallet.data.multichain.lighter.PerpsQuoteError
import java.math.BigInteger

private const val BTC_PAYMENT = "btc payment"
private const val OP_RETURN = 0x6a
private const val OP_PUSHDATA1 = 0x4c
private const val MAX_DIRECT_PUSH = 0x4b
private const val HEX_RADIX = 16
private const val VERSION_AND_LOCK_TIME_BYTES = 8L
private const val INPUT_BYTES = 41L
private const val OUTPUT_VALUE_BYTES = 8L
private const val SEGWIT_MARKER_AND_FLAG_WEIGHT = 2L
private const val P2WPKH_WITNESS_WEIGHT = 109L
private const val P2TR_WITNESS_WEIGHT = 67L
private const val WITNESS_SCALE = 4L
private const val MAX_ONE_BYTE_COMPACT_SIZE = 0xfc
private const val MAX_THREE_BYTE_COMPACT_SIZE = 0xffff
private val P2WPKH_SCRIPT = Regex("0014[0-9a-f]{40}")
private val P2TR_SCRIPT = Regex("5120[0-9a-f]{64}")
private val ORDER_ID = Regex("0x[0-9a-fA-F]{64}")
private const val SIGHASH_DEFAULT = 0x00L
private const val SIGHASH_ALL = 0x01L

// ×3 rather than the ticket's ×2, so that honest deposits always go through: Relay pays the miners more than the
// wallet would, and at ×2 a dip in the wallet's own estimate is enough to refuse them.
private val MAX_FEE_RATE_MULTIPLIER: BigInteger = BigInteger.valueOf(3L)

internal class PerpsBtcDeposit(
    val operation: Operation,
    val sent: BigInteger,
    val fee: BigInteger,
    val virtualSize: Long,
)

internal object PerpsBtcDepositReader {

    fun read(
        provider: PerpetualDepositProvider,
        payment: Operation,
        sender: String,
        amountUnits: BigInteger,
        security: PerpsSecurityConfig?,
    ): PerpsBtcDeposit {
        if (provider != PerpetualDepositProvider.Relay) {
            refuse("provider")
        }
        val depositories = security?.btcRelayDepositories.orEmpty()
        if (depositories.isEmpty()) {
            refuse("relay depository")
        }
        val payload = payment.payloads.singleOrNull() ?: refuse("payloads")
        if (payload.type != Payload.Type.Prebuild || payload.chain != Chain.Bitcoin.Mainnet || payload.version != Payload.VERSION) {
            refuse("payload")
        }
        val psbt = payload.meta as? PayloadMeta.Bitcoin.Prebuild ?: refuse("payload")
        val inputs = psbt.inputs(payload.chain).getOrNull() ?: refuse("psbt")
        val outputs = psbt.outputs(payload.chain).getOrNull() ?: refuse("psbt")
        val owner = btcAddress(sender) ?: refuse("sender")

        val spent = inputs.map { input ->
            val coin = input.spent?.takeIf { it.owner() == owner } ?: refuse("input")
            if (input.sighash != null && input.sighash != SIGHASH_ALL && !(input.sighash == SIGHASH_DEFAULT && coin.isTaproot())) {
                refuse("sighash")
            }
            coin
        }

        val (deposits, rest) = outputs.partition { it.owner() in depositories }
        val (change, others) = rest.partition { it.owner() == owner }
        val deposit = deposits.singleOrNull() ?: refuse("depository output")
        if (change.size > 1) {
            refuse("change output")
        }
        val order = others.singleOrNull() ?: refuse("outputs")
        if (!order.value.isZero() || !order.carriesOrderId()) {
            refuse("op_return")
        }

        val sent = deposit.value.toJava()
        if (!fitsDepositBudget(sent, amountUnits)) {
            refuse("value")
        }

        val fee = spent.sumOf { it.value.toJava() } - outputs.sumOf { it.value.toJava() }
        if (fee.signum() < 0) {
            refuse("fee")
        }

        return PerpsBtcDeposit(
            operation = Operation.build(payment.id, listOf(payload.copy(fee = Fee.Value(payload.chain.toAsset(), fee.toChain())))),
            sent = sent,
            fee = fee,
            virtualSize = virtualSize(spent, outputs),
        )
    }

    fun checkFeeRate(deposit: PerpsBtcDeposit, walletFeeRate: BigInteger?) {
        val rate = walletFeeRate?.takeIf { it.signum() > 0 } ?: refuse("wallet fee rate")
        if (deposit.fee > rate * MAX_FEE_RATE_MULTIPLIER * BigInteger.valueOf(deposit.virtualSize)) {
            refuse("fee rate")
        }
    }

    private fun virtualSize(spent: List<PsbtOutput>, outputs: List<PsbtOutput>): Long {
        val outputBytes = outputs.sumOf { output ->
            val scriptBytes = output.script.length / 2L
            OUTPUT_VALUE_BYTES + compactSizeBytes(scriptBytes) + scriptBytes
        }
        val baseBytes = VERSION_AND_LOCK_TIME_BYTES +
            compactSizeBytes(spent.size.toLong()) + spent.size * INPUT_BYTES +
            compactSizeBytes(outputs.size.toLong()) + outputBytes
        val witnessWeight = SEGWIT_MARKER_AND_FLAG_WEIGHT + spent.sumOf { witnessWeight(it) }
        return (baseBytes * WITNESS_SCALE + witnessWeight + WITNESS_SCALE - 1) / WITNESS_SCALE
    }

    private fun witnessWeight(spent: PsbtOutput): Long {
        return when {
            P2WPKH_SCRIPT.matches(spent.script.lowercase()) -> P2WPKH_WITNESS_WEIGHT
            spent.isTaproot() -> P2TR_WITNESS_WEIGHT
            else -> refuse("input")
        }
    }

    private fun PsbtOutput.isTaproot(): Boolean = P2TR_SCRIPT.matches(script.lowercase())

    private fun compactSizeBytes(value: Long): Long = when {
        value <= MAX_ONE_BYTE_COMPACT_SIZE -> 1L
        value <= MAX_THREE_BYTE_COMPACT_SIZE -> 3L
        else -> 5L
    }

    private fun PsbtOutput.owner(): String? = address?.display?.let(::btcAddress)

    private fun PsbtOutput.carriesOrderId(): Boolean {
        if (address != null || script.length % 2 != 0) {
            return false
        }
        val bytes = script.chunked(2).map { it.toIntOrNull(HEX_RADIX) ?: return false }
        if (bytes.firstOrNull() != OP_RETURN || bytes.size < 2) {
            return false
        }
        val (length, start) = when (val push = bytes[1]) {
            in 1..MAX_DIRECT_PUSH -> push to 2
            OP_PUSHDATA1 -> (bytes.getOrNull(2) ?: return false) to 3
            else -> return false
        }
        if (bytes.size != start + length) {
            return false
        }
        val text = bytes.subList(start, bytes.size).map { it.toByte() }.toByteArray().toString(Charsets.US_ASCII)
        return ORDER_ID.matches(text)
    }

    private fun refuse(field: String): Nothing {
        throw PerpsDepositException.QuoteRejected(PerpsQuoteError.UnexpectedOperation("$BTC_PAYMENT $field"))
    }
}
