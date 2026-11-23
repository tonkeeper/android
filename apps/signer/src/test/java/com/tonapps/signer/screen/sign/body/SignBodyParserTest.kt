package com.tonapps.signer.screen.sign.body

import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletV3R2Contract
import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import com.tonapps.blockchain.ton.contract.WalletV5BetaContract
import com.tonapps.blockchain.ton.contract.WalletV5R1Contract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.storeAddress
import com.tonapps.blockchain.ton.extensions.storeCoins
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.blockchain.ton.extensions.storeStringTail
import com.tonapps.blockchain.ton.tlb.JettonTransfer
import com.tonapps.blockchain.ton.tlb.NftTransfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.bitstring.BitString
import org.ton.block.AddrExtern
import org.ton.block.AddrNone
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.CommonMsgInfoRelaxed
import org.ton.block.CurrencyCollection
import org.ton.block.Either
import org.ton.block.Maybe
import org.ton.block.MessageRelaxed
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.currency.VarUInt248
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeTlb

class SignBodyParserTest {

    private val publicKey = PrivateKeyEd25519(ByteArray(32) { 1 }).publicKey()
    private val merchant = address(1)
    private val attacker = address(2)
    private val recipient = address(3)
    private val excess = address(4)
    private val jettonWallet = address(5)

    @Test
    fun v5TransferWithHiddenAddExtensionShowsTheExtension() {
        val body = v5Body(outList(3 to message(merchant, 1_000_000L))) {
            storeBit(true)
            storeUInt(EXTENDED_ADD, 8)
            storeAddress(attacker)
        }

        val actions = SignBodyParser.parse(body, "v5r1").actions

        assertEquals(2, actions.size)
        assertEquals(merchant, (actions[0] as SignAction.Send).destination)
        assertEquals(SignAction.AddExtension(attacker), actions[1])
    }

    @Test
    fun v5ExtendedActionsChainedByRefsAreAllShown() {
        val last = buildCell { storeUInt(EXTENDED_SET_SIGNATURE_AUTH, 8); storeBit(false) }
        val middle = buildCell { storeUInt(EXTENDED_DELETE, 8); storeAddress(merchant); storeRef(last) }
        val body = v5Body(outList = null) {
            storeBit(true)
            storeUInt(EXTENDED_ADD, 8)
            storeAddress(attacker)
            storeRef(middle)
        }

        val actions = SignBodyParser.parse(body, "v5r1").actions

        assertEquals(
            listOf(
                SignAction.AddExtension(attacker),
                SignAction.RemoveExtension(merchant),
                SignAction.SetSignatureAuth(false),
            ),
            actions
        )
    }

    @Test(expected = SignBodyException::class)
    fun v5TrailingBitsAfterExtendedActionAreRefused() {
        val body = v5Body(outList(3 to message(merchant, 1_000_000L))) {
            storeBit(true)
            storeUInt(EXTENDED_ADD, 8)
            storeAddress(attacker)
            storeUInt(0, 7)
        }

        SignBodyParser.parse(body, "v5r1")
    }

    @Test(expected = SignBodyException::class)
    fun v5TrailingRefAfterTransfersIsRefused() {
        val body = v5Body(outList(3 to message(merchant, 1_000_000L))) {
            storeBit(false)
            storeRef(Cell.empty())
        }

        SignBodyParser.parse(body, "v5r1")
    }

    @Test(expected = SignBodyException::class)
    fun v5UnknownExtendedActionIsRefused() {
        val body = v5Body(outList(3 to message(merchant, 1_000_000L))) {
            storeBit(true)
            storeUInt(0x7f, 8)
        }

        SignBodyParser.parse(body, "v5r1")
    }

    @Test(expected = SignBodyException::class)
    fun v5UnknownOutActionIsRefused() {
        val setCode = buildCell {
            storeRef(Cell.empty())
            storeUInt(0xad4de08eL, 32)
            storeRef(Cell.empty())
        }
        val body = v5Body(setCode) { storeBit(false) }

        SignBodyParser.parse(body, "v5r1")
    }

    @Test(expected = SignBodyException::class)
    fun v5ExternalOutMessageIsRefused() {
        val external = MessageRelaxed(
            info = CommonMsgInfoRelaxed.ExtOutMsgInfoRelaxed(
                src = AddrNone,
                dest = AddrExtern(BitString(ByteArray(4))),
                createdLt = 0u,
                createdAt = 0u
            ),
            init = Maybe.of(null),
            body = Either.of<Cell, CellRef<Cell>>(Cell.empty(), null)
        )
        val body = v5Body(outList(3 to external)) { storeBit(false) }

        SignBodyParser.parse(body, "v5r1")
    }

    @Test(expected = SignBodyException::class)
    fun messageWithExtraCurrenciesIsRefused() {
        val builder = WalletTransferBuilder()
        builder.destination = merchant
        builder.bounceable = true
        builder.currencyCollection = CurrencyCollection(Coins.ofNano(1_000_000L), mapOf(1 to VarUInt248(5L)))
        builder.messageData = MessageData.raw(Cell.empty())
        val withExtra = BaseWalletContract.createIntMsg(builder.build())
        val body = v5Body(outList(3 to withExtra)) { storeBit(false) }

        SignBodyParser.parse(body, "v5r1")
    }

    @Test(expected = SignBodyException::class)
    fun v5BodyWithoutActionsIsRefused() {
        val body = v5Body(outList = null) { storeBit(false) }

        SignBodyParser.parse(body, "v5r1")
    }

    @Test
    fun v5LegitTransfersKeepTheirOrder() {
        val contract = WalletV5R1Contract(publicKey, TonNetwork.MAINNET)
        val body = contract.createTransferUnsignedBody(
            validUntil = VALID_UNTIL,
            seqNo = 7,
            internalMessage = false,
            queryId = null,
            transfer(merchant), transfer(recipient), transfer(attacker)
        )

        val actions = SignBodyParser.parse(body, "v5r1").actions

        assertEquals(listOf(merchant, recipient, attacker), actions.map { (it as SignAction.Send).destination })
    }

    @Test
    fun sendModeEntireBalanceIsSurfaced() {
        val body = v5Body(outList(128 to message(merchant, 0L))) { storeBit(false) }

        val send = SignBodyParser.parse(body, "v5r1").actions.single() as SignAction.Send

        assertTrue(send.sendsEntireBalance)
        assertFalse(send.destroysContract)
    }

    @Test
    fun sendModeDestroyIsSurfaced() {
        val body = v5Body(outList(32 + 128 + 2 to message(merchant, 0L))) { storeBit(false) }

        val send = SignBodyParser.parse(body, "v5r1").actions.single() as SignAction.Send

        assertTrue(send.sendsEntireBalance)
        assertTrue(send.destroysContract)
    }

    @Test
    fun v4SendModeIsRead() {
        val contract = WalletV4R2Contract(publicKey = publicKey)
        val body = contract.createTransferUnsignedBody(
            validUntil = VALID_UNTIL,
            seqNo = 7,
            internalMessage = false,
            queryId = null,
            transfer(merchant, sendMode = 128 + 32)
        )

        val send = SignBodyParser.parse(body, "v4r2").actions.single() as SignAction.Send

        assertEquals(merchant, send.destination)
        assertTrue(send.sendsEntireBalance)
        assertTrue(send.destroysContract)
    }

    @Test
    fun v4DeployAndInstallPluginIsShownWithPluginAddress() {
        val stateInit = buildCell { storeUInt(0xabcdL, 16) }
        val body = v4Body(op = 1) {
            storeInt(0, 8)
            storeCoins(Coins.ofNano(50_000_000L))
            storeRef(stateInit)
            storeRef(Cell.empty())
        }

        val action = SignBodyParser.parse(body, "v4r2").actions.single()

        assertEquals(SignAction.InstallPlugin(AddrStd(0, stateInit.hash()), Coins.ofNano(50_000_000L)), action)
    }

    @Test
    fun v4InstallPluginIsShownWithPluginAddress() {
        val body = v4Body(op = 2) {
            storeInt(attacker.workchainId, 8)
            storeBits(attacker.address)
            storeCoins(Coins.ofNano(10_000_000L))
            storeUInt(42, 64)
        }

        val action = SignBodyParser.parse(body, "v4r2").actions.single()

        assertEquals(SignAction.InstallPlugin(attacker, Coins.ofNano(10_000_000L)), action)
    }

    @Test
    fun v4RemovePluginIsShownWithPluginAddress() {
        val body = WalletV4R2Contract(publicKey = publicKey).removePlugin(
            seqNo = 7,
            validUntil = VALID_UNTIL,
            queryId = 42.toBigInteger(),
            forwardAmount = Coins.ofNano(50_000_000L),
            pluginAddress = attacker
        )

        val action = SignBodyParser.parse(body, "v4r2").actions.single()

        assertEquals(SignAction.RemovePlugin(attacker, Coins.ofNano(50_000_000L)), action)
    }

    @Test(expected = SignBodyException::class)
    fun v4UnknownOpIsRefused() {
        val body = v4Body(op = 7) {
            storeUInt(3, 8)
            storeRef(messageCell(message(merchant, 1_000_000L)))
        }

        SignBodyParser.parse(body, "v4r2")
    }

    @Test(expected = SignBodyException::class)
    fun v4TrailingBitsAreRefused() {
        val body = v4Body(op = 0) {
            storeUInt(3, 8)
            storeRef(messageCell(message(merchant, 1_000_000L)))
            storeUInt(1, 3)
        }

        SignBodyParser.parse(body, "v4r2")
    }

    @Test
    fun jettonTransferShowsAttachedTonAndExcess() {
        val jettonBody = buildCell {
            storeTlb(
                JettonTransfer.tlbCodec(),
                JettonTransfer(
                    queryId = 0,
                    coins = Coins.ofNano(1L),
                    toAddress = recipient,
                    responseAddress = excess,
                    forwardAmount = Coins.ofNano(1L),
                    comment = null
                )
            )
        }
        val body = v5Body(outList(3 to message(jettonWallet, 4_200_000_000L, jettonBody))) { storeBit(false) }

        val send = SignBodyParser.parse(body, "v5r1").actions.single() as SignAction.Send

        assertEquals(jettonWallet, send.destination)
        assertEquals(Coins.ofNano(4_200_000_000L), send.value)
        assertEquals(SendPayload.Jetton(recipient = recipient, excess = excess), send.payload)
    }

    @Test
    fun nftTransferShowsAttachedTonAndExcess() {
        val nftBody = buildCell {
            storeTlb(
                NftTransfer.tlbCodec(),
                NftTransfer(
                    newOwnerAddress = recipient,
                    excessesAddress = excess,
                    forwardAmount = Coins.ofNano(1L),
                    comment = null
                )
            )
        }
        val body = v4Body(op = 0) {
            storeUInt(3, 8)
            storeRef(messageCell(message(jettonWallet, 2_000_000_000L, nftBody)))
        }

        val send = SignBodyParser.parse(body, "v4r2").actions.single() as SignAction.Send

        assertEquals(Coins.ofNano(2_000_000_000L), send.value)
        assertEquals(SendPayload.Nft(newOwner = recipient, excess = excess), send.payload)
    }

    @Test
    fun unknownInternalOpIsSurfaced() {
        val callBody = buildCell { storeUInt(0xdeadbeefL, 32) }
        val body = v5Body(outList(3 to message(merchant, 1_000_000L, callBody))) { storeBit(false) }

        val send = SignBodyParser.parse(body, "v5r1").actions.single() as SignAction.Send

        assertEquals(SendPayload.Unknown(0xdeadbeefL), send.payload)
    }

    @Test
    fun textCommentIsShown() {
        val commentBody = buildCell { storeUInt(0, 32); storeStringTail("hello") }
        val body = v5Body(outList(3 to message(merchant, 1_000_000L, commentBody))) { storeBit(false) }

        val send = SignBodyParser.parse(body, "v5r1").actions.single() as SignAction.Send

        assertEquals(SendPayload.Ton, send.payload)
        assertEquals("hello", send.comment)
    }

    @Test
    fun keeperW5VersionNamesAreAccepted() {
        assertEquals(WalletVersion.V5R1, SignBodyParser.parse(legitV5R1(), "w5").version)
        assertEquals(WalletVersion.V5BETA, SignBodyParser.parse(legitV5Beta(), "w5 beta").version)
    }

    @Test
    fun versionIsDetectedWhenTheRequestOmitsIt() {
        assertEquals(WalletVersion.V5R1, SignBodyParser.parse(legitV5R1(), "").version)
        assertEquals(WalletVersion.V5BETA, SignBodyParser.parse(legitV5Beta(), "").version)
        assertEquals(WalletVersion.V4R2, SignBodyParser.parse(legitV4R2(), "").version)
        assertEquals(WalletVersion.V3R2, SignBodyParser.parse(legitV3R2(), "").version)
    }

    @Test(expected = SignBodyException::class)
    fun bodyOfAnotherVersionThanRequestedIsRefused() {
        SignBodyParser.parse(legitV5R1(), "v4r2")
    }

    private fun legitV5R1(): Cell {
        return WalletV5R1Contract(publicKey, TonNetwork.MAINNET)
            .createTransferUnsignedBody(VALID_UNTIL, 7, false, null, transfer(merchant))
    }

    private fun legitV5Beta(): Cell {
        return WalletV5BetaContract(publicKey = publicKey, networkGlobalId = TonNetwork.MAINNET.value)
            .createTransferUnsignedBody(VALID_UNTIL, 7, false, null, transfer(merchant))
    }

    private fun legitV4R2(): Cell {
        return WalletV4R2Contract(publicKey = publicKey)
            .createTransferUnsignedBody(VALID_UNTIL, 7, false, null, transfer(merchant))
    }

    private fun legitV3R2(): Cell {
        return WalletV3R2Contract(publicKey = publicKey)
            .createTransferUnsignedBody(VALID_UNTIL, 7, false, null, transfer(merchant))
    }

    private fun v5Body(outList: Cell?, tail: CellBuilder.() -> Unit): Cell {
        return buildCell {
            storeOpCode(TONOpCode.SIGNED_EXTERNAL)
            storeUInt(0, 32)
            storeUInt(VALID_UNTIL, 32)
            storeUInt(7, 32)
            if (outList == null) {
                storeBit(false)
            } else {
                storeBit(true)
                storeRef(outList)
            }
            tail()
        }
    }

    private fun v4Body(op: Int, tail: CellBuilder.() -> Unit): Cell {
        return buildCell {
            storeUInt(BaseWalletContract.DEFAULT_WALLET_ID, 32)
            storeUInt(VALID_UNTIL, 32)
            storeUInt(7, 32)
            storeUInt(op, 8)
            tail()
        }
    }

    private fun outList(vararg actions: Pair<Int, MessageRelaxed<Cell>>): Cell {
        var list = Cell.empty()
        for ((sendMode, message) in actions) {
            val prev = list
            list = buildCell {
                storeRef(prev)
                storeOpCode(TONOpCode.OUT_ACTION_SEND_MSG_TAG)
                storeUInt(sendMode, 8)
                storeRef(messageCell(message))
            }
        }
        return list
    }

    private fun message(to: AddrStd, nanoTon: Long, body: Cell = Cell.empty()): MessageRelaxed<Cell> {
        return BaseWalletContract.createIntMsg(transfer(to, nanoTon, body))
    }

    private fun messageCell(message: MessageRelaxed<Cell>): Cell {
        return buildCell { storeTlb(MessageRelaxed.tlbCodec(AnyTlbConstructor), message) }
    }

    private fun transfer(
        to: AddrStd,
        nanoTon: Long = 100_000_000L,
        body: Cell = Cell.empty(),
        sendMode: Int = 3
    ): WalletTransfer {
        val builder = WalletTransferBuilder()
        builder.destination = to
        builder.bounceable = true
        builder.coins = Coins.ofNano(nanoTon)
        builder.sendMode = sendMode
        builder.messageData = MessageData.raw(body)
        return builder.build()
    }

    private fun address(seed: Int) = AddrStd(0, ByteArray(32) { seed.toByte() })
}

private const val VALID_UNTIL = 1_900_000_000L
private const val EXTENDED_ADD = 2
private const val EXTENDED_DELETE = 3
private const val EXTENDED_SET_SIGNATURE_AUTH = 4
