package ton.contract

import kotlinx.datetime.Clock
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrNone
import org.ton.block.Coins
import org.ton.block.CommonMsgInfoRelaxed
import org.ton.block.Either
import org.ton.block.ExtInMsgInfo
import org.ton.block.Maybe
import org.ton.block.Message
import org.ton.block.MessageRelaxed
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.contract.SmartContract
import org.ton.contract.wallet.WalletContract
import org.ton.contract.wallet.WalletTransfer
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeRef
import org.ton.tlb.storeTlb
import ton.wallet.Wallet
import kotlin.time.Duration.Companion.seconds

abstract class BaseWalletContract(
    val workchain: Int = DEFAULT_WORKCHAIN,
    val publicKey: PublicKeyEd25519
) {

    companion object {
        const val DEFAULT_WORKCHAIN = 0
        const val DEFAULT_WALLET_ID: Int = 698983191
        const val OP_JETTON = 0xf8a7ea5

        fun createIntMsg(gift: WalletTransfer): MessageRelaxed<Cell> {
            val info = CommonMsgInfoRelaxed.IntMsgInfoRelaxed(
                ihrDisabled = true,
                bounce = gift.bounceable,
                bounced = false,
                src = AddrNone,
                dest = gift.destination,
                value = gift.coins,
                ihrFee = Coins(),
                fwdFee = Coins(),
                createdLt = 0u,
                createdAt = 0u
            )
            val init = Maybe.of(gift.stateInit?.let {
                Either.of<StateInit, CellRef<StateInit>>(null, CellRef(it))
            })
            val body = if (gift.body == null) {
                Either.of<Cell, CellRef<Cell>>(Cell.empty(), null)
            } else {
                Either.of<Cell, CellRef<Cell>>(null, CellRef(gift.body!!))
            }

            return MessageRelaxed(
                info = info,
                init = init,
                body = body,
            )
        }

        fun createJettonBody(
            queryId: Long = System.currentTimeMillis(),
            jettonCoins: Coins,
            toAddress: MsgAddressInt,
            responseAddress: MsgAddressInt,
            forwardAmount: Long,
            forwardPayload: Any?
        ): Cell {
            val payload = prepareForwardPayload(forwardPayload)

            return CellBuilder.createCell {
                storeUInt(OP_JETTON, 32)
                storeUInt(queryId, 64)
                storeTlb(Coins, jettonCoins)
                storeTlb(MsgAddressInt, toAddress)
                storeTlb(MsgAddressInt, responseAddress)
                storeBit(false)
                storeTlb(Coins, Coins.ofNano(forwardAmount))
                storeBit(payload != null)
                if (payload != null) {
                    storeRef(AnyTlbConstructor, CellRef(payload))
                }
            }
        }

        private fun prepareForwardPayload(forwardPayload: Any?): Cell? {
            if (forwardPayload == null) {
                return null
            }
            return when (forwardPayload) {
                is String -> buildCommentBody(forwardPayload)
                is Cell -> forwardPayload
                else -> null
            }
        }

        fun buildCommentBody(text: String?): Cell? {
            if (text.isNullOrEmpty()) {
                return null
            }
            return buildCell {
                storeUInt(0, 32)
                storeBytes(text.toByteArray())
            }
        }
    }

    val walletId = DEFAULT_WALLET_ID + workchain

    val stateInit: StateInit by lazy {
        val cell = getStateCell()
        val code = getCode()
        StateInit(code, cell)
    }

    val address: MsgAddressInt by lazy {
        SmartContract.address(workchain, stateInit)
    }

    abstract fun getStateCell(): Cell

    abstract fun getCode(): Cell

    abstract fun createTransferMessageBody(
        privateKey: PrivateKeyEd25519,
        validUntil: Long,
        seqno: Int,
        vararg gifts: WalletTransfer
    ): Cell


    fun createTransferMessage(
        address: MsgAddressInt,
        privateKey: PrivateKeyEd25519,
        validUntil: Long = (Clock.System.now() + 60.seconds).epochSeconds,
        seqno: Int,
        vararg transfers: WalletTransfer
    ): Message<Cell> {
        val info = ExtInMsgInfo(
            src = AddrNone,
            dest = address,
            importFee = Coins()
        )

        val init = if (seqno == 0) {
            stateInit
        } else null

        val maybeStateInit = Maybe.of(init?.let { Either.of<StateInit, CellRef<StateInit>>(null, CellRef(it)) })

        val transferBody = createTransferMessageBody(
            privateKey,
            validUntil,
            seqno,
            *transfers
        )
        val body = Either.of<Cell, CellRef<Cell>>(null, CellRef(transferBody))
        return Message(
            info = info,
            init = maybeStateInit,
            body = body
        )
    }
}