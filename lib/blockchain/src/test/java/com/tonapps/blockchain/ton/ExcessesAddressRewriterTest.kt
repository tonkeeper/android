package com.tonapps.blockchain.ton

import com.tonapps.blockchain.ton.extensions.loadAddress
import com.tonapps.blockchain.ton.extensions.loadCoins
import com.tonapps.blockchain.ton.extensions.loadMaybeRef
import com.tonapps.blockchain.ton.extensions.loadOpCode
import com.tonapps.blockchain.ton.extensions.storeAddress
import com.tonapps.blockchain.ton.extensions.storeCoins
import com.tonapps.blockchain.ton.extensions.storeMaybeRef
import com.tonapps.blockchain.ton.extensions.storeOpCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import org.ton.cell.buildCell

class ExcessesAddressRewriterTest {

    private val user = address(1)
    private val battery = address(2)
    private val router = address(3)
    private val tokenWallet = address(4)

    @Test
    fun jettonTransferResponseDestinationPointsAtBattery() {
        val payload = jettonTransfer(response = user, forward = null)

        val rewritten = ExcessesAddressRewriter.rewrite(payload, battery)

        val slice = rewritten.beginParse()
        assertEquals(TONOpCode.JETTON_TRANSFER, slice.loadOpCode())
        assertEquals(QUERY_ID, slice.loadUInt(64).toLong())
        assertEquals(Coins.ofNano(AMOUNT), slice.loadCoins())
        assertEquals(router, slice.loadAddress())
        assertEquals(battery, slice.loadAddress())
        assertEquals(null, slice.loadMaybeRef())
        assertEquals(Coins.ofNano(FORWARD_AMOUNT), slice.loadCoins())
        assertEquals(false, slice.loadBit())
        assertEquals(0, slice.remainingBits)
        assertEquals(0, slice.remainingRefs)
    }

    @Test
    fun stonfiV2ExcessesInsideJettonForwardPayloadPointsAtBattery() {
        val payload = jettonTransfer(response = user, forward = stonfiSwapV2(refund = user, excesses = user))

        val rewritten = ExcessesAddressRewriter.rewrite(payload, battery)

        val transfer = rewritten.beginParse()
        skipJettonTransferHead(transfer)
        assertEquals(battery, transfer.loadAddress())
        transfer.loadMaybeRef()
        transfer.loadCoins()
        assertEquals(true, transfer.loadBit())

        val swap = transfer.loadRef().beginParse()
        assertEquals(TONOpCode.STONFI_SWAP_V2, swap.loadOpCode())
        assertEquals(tokenWallet, swap.loadAddress())
        assertEquals(user, swap.loadAddress())
        assertEquals(battery, swap.loadAddress())
        assertEquals(DEADLINE, swap.loadUInt(64).toLong())
        assertEquals(crossSwapBody(), swap.loadRef())
        assertEquals(0, swap.remainingBits)
    }

    @Test
    fun inlineForwardPayloadIsKeptWhenItIsNotRewritable() {
        val comment = buildCell {
            storeUInt(0, 32)
            storeBytes("hello".encodeToByteArray())
        }
        val payload = buildCell {
            storeJettonTransferHead(response = user)
            storeBit(false)
            storeBits(comment.bits)
        }

        val rewritten = ExcessesAddressRewriter.rewrite(payload, battery)

        val transfer = rewritten.beginParse()
        skipJettonTransferHead(transfer)
        assertEquals(battery, transfer.loadAddress())
        transfer.loadMaybeRef()
        transfer.loadCoins()
        assertEquals(false, transfer.loadBit())
        assertEquals(comment.bits, transfer.loadBits(transfer.remainingBits))
        assertEquals(0, rewritten.refs.size)
    }

    @Test
    fun ptonTransferDescendsIntoForwardPayloadAndKeepsRefund() {
        val payload = buildCell {
            storeOpCode(TONOpCode.PTON_TON_TRANSFER)
            storeUInt(QUERY_ID, 64)
            storeCoins(Coins.ofNano(AMOUNT))
            storeAddress(user)
            storeBit(true)
            storeRef(stonfiSwapV2(refund = user, excesses = user))
        }

        val rewritten = ExcessesAddressRewriter.rewrite(payload, battery)

        val slice = rewritten.beginParse()
        assertEquals(TONOpCode.PTON_TON_TRANSFER, slice.loadOpCode())
        assertEquals(QUERY_ID, slice.loadUInt(64).toLong())
        assertEquals(Coins.ofNano(AMOUNT), slice.loadCoins())
        assertEquals(user, slice.loadAddress())
        assertEquals(true, slice.loadBit())
        val swap = slice.loadRef().beginParse()
        swap.loadOpCode()
        swap.loadAddress()
        assertEquals(user, swap.loadAddress())
        assertEquals(battery, swap.loadAddress())
    }

    @Test
    fun stonfiV1ReferralSlotPointsAtBattery() {
        val v1 = buildCell {
            storeOpCode(TONOpCode.STONFI_SWAP)
            storeAddress(tokenWallet)
            storeCoins(Coins.ofNano(AMOUNT))
            storeAddress(user)
            storeBit(true)
            storeAddress(router)
        }
        val payload = jettonTransfer(response = user, forward = v1)

        val rewritten = ExcessesAddressRewriter.rewrite(payload, battery)

        val transfer = rewritten.beginParse()
        skipJettonTransferHead(transfer)
        assertEquals(battery, transfer.loadAddress())
        transfer.loadMaybeRef()
        transfer.loadCoins()
        transfer.loadBit()
        val swap = transfer.loadRef().beginParse()
        assertEquals(TONOpCode.STONFI_SWAP, swap.loadOpCode())
        assertEquals(tokenWallet, swap.loadAddress())
        assertEquals(Coins.ofNano(AMOUNT), swap.loadCoins())
        assertEquals(user, swap.loadAddress())
        assertEquals(true, swap.loadBit())
        assertEquals(battery, swap.loadAddress())
        assertEquals(0, swap.remainingBits)
    }

    @Test
    fun unknownAndShortPayloadsAreReturnedUntouched() {
        val comment = buildCell {
            storeUInt(0, 32)
            storeBytes("hello".encodeToByteArray())
        }
        val short = buildCell { storeUInt(5, 8) }
        val nft = buildCell {
            storeOpCode(TONOpCode.NFT_TRANSFER)
            storeUInt(QUERY_ID, 64)
            storeAddress(router)
            storeAddress(user)
        }

        assertSame(comment, ExcessesAddressRewriter.rewrite(comment, battery))
        assertSame(short, ExcessesAddressRewriter.rewrite(short, battery))
        assertSame(nft, ExcessesAddressRewriter.rewrite(nft, battery))
        assertSame(Cell.empty(), ExcessesAddressRewriter.rewrite(Cell.empty(), battery))
    }

    @Test
    fun malformedKnownOpCodeFallsBackToOriginal() {
        val truncated = buildCell {
            storeOpCode(TONOpCode.JETTON_TRANSFER)
            storeUInt(QUERY_ID, 64)
        }

        assertSame(truncated, ExcessesAddressRewriter.rewrite(truncated, battery))
    }

    private fun jettonTransfer(response: MsgAddressInt, forward: Cell?): Cell = buildCell {
        storeJettonTransferHead(response)
        storeMaybeRef(forward)
    }

    private fun org.ton.cell.CellBuilder.storeJettonTransferHead(response: MsgAddressInt) {
        storeOpCode(TONOpCode.JETTON_TRANSFER)
        storeUInt(QUERY_ID, 64)
        storeCoins(Coins.ofNano(AMOUNT))
        storeAddress(router)
        storeAddress(response)
        storeMaybeRef(null)
        storeCoins(Coins.ofNano(FORWARD_AMOUNT))
    }

    private fun skipJettonTransferHead(slice: CellSlice) {
        slice.loadOpCode()
        slice.loadUInt(64)
        slice.loadCoins()
        slice.loadAddress()
    }

    private fun stonfiSwapV2(refund: MsgAddressInt, excesses: MsgAddressInt): Cell = buildCell {
        storeOpCode(TONOpCode.STONFI_SWAP_V2)
        storeAddress(tokenWallet)
        storeAddress(refund)
        storeAddress(excesses)
        storeUInt(DEADLINE, 64)
        storeRef(crossSwapBody())
    }

    private fun crossSwapBody(): Cell = buildCell {
        storeCoins(Coins.ofNano(1))
        storeAddress(user)
    }

    private fun address(byte: Int): AddrStd = AddrStd(0, ByteArray(32) { byte.toByte() })

    private companion object {
        const val QUERY_ID = 7_654_321L
        const val AMOUNT = 5_000_000L
        const val FORWARD_AMOUNT = 250_000_000L
        const val DEADLINE = 1_800_000_000L
    }
}
