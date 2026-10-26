package com.tonapps.blockchain.ton.contract

import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.loadOpCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.ton.bitstring.BitString
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.CommonMsgInfoRelaxed
import org.ton.block.MessageRelaxed
import org.ton.block.invoke
import org.ton.cell.Cell
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.loadTlb

class SignedInternalMessageTest {

    private val privateKey = PrivateKeyEd25519(ByteArray(32) { 1 })
    private val contract = WalletV5R1Contract(privateKey.publicKey(), TonNetwork.MAINNET)
    private val recipient = AddrStd.parse("Ef8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADAU")

    @Test
    fun addressesInternalMessageToWalletItself() {
        val message = signedInternalMessage(seqno = 7)

        val info = message.info as CommonMsgInfoRelaxed.IntMsgInfoRelaxed
        assertEquals(contract.address, info.dest)
        assertFalse(info.bounce)
        assertEquals(Coins(), info.value.coins)
    }

    @Test
    fun carriesInternalSignedRequestWithSendModeThree() {
        val message = signedInternalMessage(seqno = 7)

        val body = bodyCell(message).beginParse()
        assertEquals(TONOpCode.SIGNED_INTERNAL, body.loadOpCode())
        body.loadBits(32 + 32 + 32) // wallet id, seqno, valid until
        assertEquals(true, body.loadBit()) // out-actions present
        assertEquals(listOf(3, 3), sendModes(body.loadRef()))
    }

    @Test
    fun keepsSignedBodyUntouched() {
        val signedBody = signedBody(seqno = 7)

        val message = parse(contract.createSignedInternalMessageCell(7, signedBody))

        assertEquals(signedBody.hash(), bodyCell(message).hash())
    }

    @Test
    fun attachesStateInitOnlyForUndeployedWallet() {
        val deployed = signedInternalMessage(seqno = 7)
        val undeployed = signedInternalMessage(seqno = 0)

        assertNull(deployed.init.value)
        val init = undeployed.init.value
        assertNotNull(init)
        assertEquals(contract.stateInitCell().hash(), init!!.y!!.hash())
    }

    private fun signedInternalMessage(seqno: Int): MessageRelaxed<Cell> {
        return parse(contract.createSignedInternalMessageCell(seqno, signedBody(seqno)))
    }

    private fun signedBody(seqno: Int): Cell {
        val unsigned = contract.createTransferUnsignedBody(
            validUntil = 1_900_000_000L,
            seqNo = seqno,
            internalMessage = true,
            queryId = null,
            transfer(), transfer()
        )
        val signature = BitString(privateKey.signToByteArray(unsigned.hash().toByteArray()))
        return contract.signedBody(signature, unsigned)
    }

    private fun transfer(): WalletTransfer {
        val builder = WalletTransferBuilder()
        builder.destination = recipient
        builder.bounceable = true
        builder.coins = Coins.ofNano(50_000_000L)
        builder.sendMode = 3
        builder.messageData = MessageData.raw(Cell.empty())
        return builder.build()
    }

    private fun parse(cell: Cell): MessageRelaxed<Cell> {
        return cell.parse { loadTlb(MessageRelaxed.tlbCodec(AnyTlbConstructor)) }
    }

    private fun bodyCell(message: MessageRelaxed<Cell>): Cell {
        return message.body.x ?: message.body.y!!.toCell()
    }

    private fun sendModes(outList: Cell): List<Int> {
        val slice = outList.beginParse()
        if (slice.bits.isEmpty()) {
            return emptyList()
        }
        val prev = slice.loadRef()
        assertEquals(TONOpCode.OUT_ACTION_SEND_MSG_TAG, slice.loadOpCode())
        val mode = slice.loadUInt(8).toInt()
        return sendModes(prev) + mode
    }
}
