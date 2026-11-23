package com.tonapps.blockchain.ton.contract

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.storeMaybeRef
import io.ktor.util.hex
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MessageRelaxed
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.contract.wallet.WalletTransfer
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeTlb
import java.math.BigInteger

// https://docs.ton.org/contracts/standard/wallets/telegram/messages
class WalletTgContract(
    publicKey: PublicKeyEd25519,
    networkGlobalId: Int,
) : BaseWalletContract(DEFAULT_WORKCHAIN, publicKey) {

    data class MessageToSend(
        val sendMode: Int,
        val cell: Cell,
    )

    private val subwalletId: Long = if (networkGlobalId == TonNetwork.MAINNET.value) {
        MAINNET_SUBWALLET_ID
    } else {
        TESTNET_SUBWALLET_ID
    }

    override val features: WalletFeature = WalletFeature.NONE

    override val maxMessages: Int = 255

    constructor(publicKey: PublicKeyEd25519, network: TonNetwork) : this(
        publicKey = publicKey,
        networkGlobalId = network.value
    )

    override fun getStateCell(): Cell {
        return CellBuilder.createCell {
            storeUInt(0, 8)
            storeUInt(0, 32)
            storeUInt(subwalletId, 32)
            storeBytes(publicKey.key.toByteArray())
        }
    }

    override fun getCode(): Cell {
        return CODE
    }

    override fun getWalletVersion() = WalletVersion.WALLET_TG

    override fun createTransferUnsignedBody(
        validUntil: Long,
        seqNo: Int,
        internalMessage: Boolean,
        queryId: BigInteger?,
        vararg gifts: WalletTransfer
    ): Cell {
        val messages = gifts.map { gift ->
            MessageToSend(
                sendMode = gift.sendMode,
                cell = buildCell {
                    storeTlb(MessageRelaxed.tlbCodec(AnyTlbConstructor), createIntMsg(gift))
                }
            )
        }
        return createRequestBody(validUntil, seqNo, internalMessage, messages)
    }

    fun createRequestBody(
        validUntil: Long,
        seqNo: Int,
        internalMessage: Boolean,
        messages: List<MessageToSend>,
    ): Cell {
        require(messages.size in 1..maxMessages) {
            "WalletTg sends from 1 to $maxMessages messages in a single transfer, got ${messages.size}"
        }
        // An external request without the ignore-errors mode is rejected with exit code 137.
        require(internalMessage || messages.all { it.sendMode and SEND_MODE_IGNORE_ERRORS != 0 }) {
            "WalletTg requires the ignore-errors send mode in external requests"
        }

        val single = messages.size == 1
        val opCode = when {
            single && internalMessage -> SEND_ONE_INTERNAL
            single -> SEND_ONE_EXTERNAL
            internalMessage -> SEND_BULK_INTERNAL
            else -> SEND_BULK_EXTERNAL
        }

        return buildCell {
            storeUInt(opCode, 32)
            storeUInt(subwalletId, 32)
            storeUInt(validUntil, 32)
            storeUInt(seqNo, 32)
            if (single) {
                storeMessage(messages.first())
            } else {
                storeMessages(messages)
            }
        }
    }

    // Tolk array<MessageToSend>: len:uint8 head:(Maybe ^Chunk), Chunk = next:(Maybe ^Chunk) items.
    // Chunks are filled from the tail, so only the head chunk may be partial.
    private fun CellBuilder.storeMessages(messages: List<MessageToSend>) {
        storeUInt(messages.size, 8)
        var next: Cell? = null
        var rest = messages
        while (rest.isNotEmpty()) {
            val capacity = if (next == null) {
                CELL_REFS_LIMIT
            } else {
                CELL_REFS_LIMIT - 1
            }
            val size = minOf(rest.size, capacity)
            val chunk = buildCell {
                storeMaybeRef(next)
                rest.takeLast(size).forEach { storeMessage(it) }
            }
            rest = rest.dropLast(size)
            next = chunk
        }
        storeMaybeRef(next)
    }

    private fun CellBuilder.storeMessage(message: MessageToSend) {
        storeUInt(message.sendMode, 8)
        storeRef(message.cell)
    }

    override fun removePlugin(
        seqNo: Int,
        validUntil: Long,
        queryId: BigInteger,
        forwardAmount: Coins,
        pluginAddress: AddrStd
    ): Cell {
        throw UnsupportedOperationException("WalletTg has no plugins")
    }

    companion object {
        private const val MAINNET_SUBWALLET_ID = 0x7FFF7F11L
        private const val TESTNET_SUBWALLET_ID = 0x7FFF7FFDL

        private const val SEND_ONE_INTERNAL = 0x63896E74L
        private const val SEND_ONE_EXTERNAL = 0x63896E75L
        private const val SEND_BULK_INTERNAL = 0x73896E74L
        private const val SEND_BULK_EXTERNAL = 0x73896E75L

        private const val SEND_MODE_IGNORE_ERRORS = 2
        private const val CELL_REFS_LIMIT = 4

        @JvmField
        val CODE = BagOfCells(hex("b5ee9c7241010101001a000030ff00209821d7498308b9f240df8085f833d0ed1e20ed53d969427e39")).first()
    }
}