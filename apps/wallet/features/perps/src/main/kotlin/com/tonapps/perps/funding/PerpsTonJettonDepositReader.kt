package com.tonapps.perps.funding

import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.blockchain.ton.toBigInteger
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.perps.security.tonAccountId
import com.tonapps.wallet.data.multichain.lighter.PerpetualDepositProvider
import com.tonapps.wallet.data.multichain.lighter.PerpsQuoteError
import kotlinx.coroutines.CancellationException
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddress
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.CellBuilder
import org.ton.cell.CellSlice
import org.ton.tlb.CellRef
import org.ton.tlb.loadTlb
import org.ton.tlb.storeTlb
import java.math.BigInteger

private const val JETTON_PAYMENT = "jetton payment"
private const val JETTON_TRANSFER_OP = 0x0f8a7ea5L
private const val ESCROW_ORDER_OP = 0xc0a5f0d9L
private const val OP_BITS = 32
private const val QUERY_ID_BITS = 64
private const val ORDER_QUOTE_ID_BITS = 256
private const val ORDER_FLAG_BITS = 1
private const val LIBRARY_REFERENCE_TAG = 2
private const val LIBRARY_TAG_BITS = 8
private const val JETTON_WALLET_STATUS_BITS = 4
private const val BASECHAIN = 0
private const val HEX_RADIX = 16
private const val ALLOWED_SEND_MODE_FLAGS = 0b11
private const val USDT_WALLET_CODE_HASH = "8f452d7a4dfd74066b682365177259ed05734435be76b5fd4bd5d8af2b7c3d68"
private val MAX_ATTACHED_NANOTON: BigInteger = BigInteger.valueOf(1_000_000_000L)
private val OMNISTON_ESCROWS = setOf("0:f26cef3c97696bac09802d152aa6680413fb6ec64e75d05e18778c636d06710b")

internal object PerpsTonJettonDepositReader {

    fun read(
        provider: PerpetualDepositProvider,
        payment: Operation,
        amountUnits: BigInteger,
        sender: String,
    ): BigInteger {
        if (provider != PerpetualDepositProvider.Omniston) {
            refuse("provider")
        }
        val payload = payment.payloads.singleOrNull() ?: refuse("payloads")
        if (payload.type != Payload.Type.Main || payload.chain != Chain.Ton.Mainnet || payload.version != Payload.VERSION) {
            refuse("payload")
        }
        val message = payload.meta as? PayloadMeta.Ton.Main ?: refuse("payload")
        if (message.token != null || message.stateInit != null || !message.comment.isNullOrEmpty()) {
            refuse("message")
        }
        if (message.mode?.let { it and ALLOWED_SEND_MODE_FLAGS.inv() != 0 } == true) {
            refuse("mode")
        }
        val owner = basechainAddress(sender) ?: refuse("sender")
        if (tonAccountId(message.to.display) != usdtWallet(owner).toAccountId()) {
            refuse("jetton wallet")
        }
        val attached = message.value.toJava()
        if (attached.signum() <= 0 || attached > MAX_ATTACHED_NANOTON) {
            refuse("value")
        }
        val transfer = message.body?.let(::escrowTransfer) ?: refuse("body")
        if (transfer.amount != amountUnits) {
            refuse("amount")
        }
        if (transfer.escrow.toAccountIdOrNull() !in OMNISTON_ESCROWS) {
            refuse("escrow")
        }
        if (transfer.response.toAccountIdOrNull() != owner.toAccountId()) {
            refuse("response")
        }
        if (transfer.forwardNanoton.signum() <= 0 || transfer.forwardNanoton > attached) {
            refuse("forward amount")
        }
        if (transfer.refund.toAccountIdOrNull() != owner.toAccountId()) {
            refuse("refund")
        }
        return transfer.amount
    }

    internal fun usdtWallet(owner: AddrStd): AddrStd {
        val code = CellBuilder.createCell {
            isExotic = true
            storeUInt(LIBRARY_REFERENCE_TAG, LIBRARY_TAG_BITS)
            storeBytes(USDT_WALLET_CODE_HASH.hexToBytes())
        }
        val data = CellBuilder.createCell {
            storeUInt(0, JETTON_WALLET_STATUS_BITS)
            storeTlb(Coins, Coins.ofNano(0))
            storeTlb(MsgAddressInt, owner)
            storeTlb(MsgAddressInt, AddrStd(PerpsFundingAssetId.USDT_TON_MASTER))
        }
        val hash = CellRef(StateInit(code, data), StateInit).hash()
        return AddrStd(owner.workchainId, hash)
    }

    private fun basechainAddress(address: String): AddrStd? {
        val parsed = try {
            AddrStd(address)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return parsed.takeIf { it.workchainId == BASECHAIN }
    }

    private fun escrowTransfer(body: String): EscrowTransfer? {
        return try {
            readEscrowTransfer(body.cellFromBase64().beginParse())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun readEscrowTransfer(slice: CellSlice): EscrowTransfer? {
        if (slice.loadUInt(OP_BITS).toLong() != JETTON_TRANSFER_OP) {
            return null
        }
        slice.loadUInt(QUERY_ID_BITS)
        val amount = slice.loadTlb(Coins).toJava()
        val escrow = slice.loadTlb(MsgAddress)
        val response = slice.loadTlb(MsgAddress)
        if (slice.loadBit()) {
            return null
        }
        val forwardNanoton = slice.loadTlb(Coins).toJava()
        if (!slice.loadBit()) {
            return null
        }
        val order = slice.loadRef().beginParse()
        if (order.loadUInt(OP_BITS).toLong() != ESCROW_ORDER_OP) {
            return null
        }
        order.skipBits(ORDER_QUOTE_ID_BITS + ORDER_FLAG_BITS)
        val refund = order.loadTlb(MsgAddress)
        return EscrowTransfer(amount, escrow, response, forwardNanoton, refund)
    }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()

    private fun MsgAddress.toAccountIdOrNull(): String? = (this as? AddrStd)?.toAccountId()

    private fun Coins.toJava(): BigInteger = amount.value.toBigInteger()

    private fun refuse(field: String): Nothing {
        throw PerpsDepositException.QuoteRejected(PerpsQuoteError.UnexpectedOperation("$JETTON_PAYMENT $field"))
    }

    private class EscrowTransfer(
        val amount: BigInteger,
        val escrow: MsgAddress,
        val response: MsgAddress,
        val forwardNanoton: BigInteger,
        val refund: MsgAddress,
    )
}
