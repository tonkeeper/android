package com.tonapps.wallet.data.ledger.ton

import com.tonapps.blockchain.ton.extensions.storeStringTail
import com.tonapps.blockchain.ton.toBigInt
import com.tonapps.ledger.ton.KnownJetton
import com.tonapps.ledger.ton.LedgerKnownJettons
import com.tonapps.ledger.ton.TonPayloadFormat
import com.tonapps.ledger.ton.Transaction
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.wallet.data.ledger.LedgerTonComment
import com.trustwallet.core.theopennetwork.SigningInput
import com.trustwallet.core.theopennetwork.Transfer
import okio.ByteString
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.buildCell
import org.ton.tlb.loadTlb
import java.math.BigInteger
import java.util.Base64

internal object TonTransactionMapper {

    private const val COMMENT_OPCODE = 0
    private const val WALLET_WORKCHAIN = 0

    fun map(input: SigningInput, jettonMaster: String? = null): Transaction {
        val message = input.messages.singleOrNull()
            ?: throw LedgerFailureException(LedgerFailure.Unsupported("Ledger signs exactly one TON message at a time"))
        return Transaction(
            destination = AddrStd.parse(message.dest),
            sendMode = message.mode,
            seqno = input.sequence_number,
            timeout = input.expire_at,
            bounceable = message.bounceable,
            coins = coins(message.amount),
            stateInit = message.state_init.takeIf { it.isNotBlank() }?.let(::parseStateInit),
            payload = payload(message, jettonMaster),
        )
    }

    private fun payload(message: Transfer, jettonMaster: String?): TonPayloadFormat? {
        val jetton = message.jetton_transfer
        if (jetton != null) {
            return TonPayloadFormat.JettonTransfer(
                queryId = jetton.query_id.takeIf { it != 0L }?.let { BigInteger.valueOf(it) },
                coins = coins(jetton.jetton_amount),
                receiverAddress = AddrStd.parse(jetton.to_owner),
                excessesAddress = AddrStd.parse(jetton.response_address),
                customPayload = jetton.custom_payload.takeIf { it.isNotBlank() }?.let(::parseCell),
                forwardAmount = coins(jetton.forward_amount),
                forwardPayload = message.comment.takeIf { it.isNotEmpty() }?.let(::commentCell),
                knownJetton = knownJetton(jettonMaster),
            )
        }
        val custom = message.custom_payload
        if (!custom.isNullOrBlank()) {
            return hintOrUnsafe(parseCell(custom))
        }
        val comment = message.comment.takeIf { it.isNotEmpty() } ?: return null
        return if (LedgerTonComment.isClearSignable(comment)) {
            TonPayloadFormat.Comment(comment)
        } else {
            TonPayloadFormat.Unsafe(commentCell(comment))
        }
    }

    private fun knownJetton(master: String?): KnownJetton? {
        val address = try {
            master?.let(AddrStd::parse)
        } catch (_: IllegalArgumentException) {
            null
        } ?: return null
        val jettonId = LedgerKnownJettons.jettonId(address) ?: return null
        return KnownJetton(jettonId = jettonId, workchain = WALLET_WORKCHAIN)
    }

    private fun hintOrUnsafe(cell: Cell): TonPayloadFormat {
        val hint = try {
            TonPayloadFormat.fromCell(cell)
        } catch (_: VirtualMachineError) {
            null
        } catch (_: Exception) {
            null
        } catch (_: Error) {
            null
        }
        return hint ?: TonPayloadFormat.Unsafe(cell)
    }

    fun commentCell(comment: String): Cell = buildCell {
        storeUInt(COMMENT_OPCODE, 32)
        storeStringTail(comment)
    }

    private fun coins(amount: ByteString): Coins = Coins(BigInteger(1, amount.toByteArray()).toBigInt())

    private fun parseCell(base64: String): Cell = BagOfCells(decodeBase64(base64)).first()

    private fun parseStateInit(base64: String): StateInit = parseCell(base64).parse { loadTlb(StateInit) }

    private fun decodeBase64(value: String): ByteArray = try {
        Base64.getDecoder().decode(value)
    } catch (_: IllegalArgumentException) {
        Base64.getUrlDecoder().decode(value)
    }
}
