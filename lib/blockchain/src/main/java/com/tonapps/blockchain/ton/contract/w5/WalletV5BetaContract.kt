package com.tonapps.blockchain.ton.contract.w5

import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.MessageType
import com.tonapps.blockchain.ton.contract.SignaturePosition
import com.tonapps.blockchain.ton.contract.WalletVersion
import org.ton.api.pub.PublicKeyEd25519
import org.ton.bigint.BigInt
import org.ton.block.MessageRelaxed
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellSlice
import org.ton.contract.wallet.WalletTransfer
import org.ton.crypto.base64
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeRef

class WalletV5BetaContract(
    workchain: Int = DEFAULT_WORKCHAIN,
    publicKey: PublicKeyEd25519,
    private val networkGlobalId: Int = -239,
    private val subwalletNumber: Int = 0
) : BaseWalletContract(workchain, publicKey) {

    override fun getWalletVersion() = WalletVersion.V5R1BETA

    override fun getStateCell(): Cell {
        return CellBuilder.createCell {
            storeUInt(0, 33)
            storeSlice(storeWalletId())
            storeBits(publicKey.key)
            storeBit(false)
        }
    }

    private fun storeWalletId(): CellSlice {
        return CellBuilder.createCell {
            storeInt(networkGlobalId, 32)
            storeInt(workchain, 8)
            storeUInt(0, 8) // "v5" to 0
            storeUInt(subwalletNumber, 32)
        }.beginParse()
    }

    override fun getSignaturePosition(): SignaturePosition {
        return SignaturePosition.Tail
    }

    override fun getCode(): Cell {
        return CODE
    }

    private fun storeOutList(messages: Array<out WalletTransfer>): Cell {
        var latestCell = CellBuilder.createCell {}

        for (message in messages) {
            latestCell = CellBuilder.createCell {
                storeUInt(OpCodes.ActionSendMsg.code, 32)
                storeUInt(message.sendMode, 8)
                storeRef(latestCell)
                val intMsg = CellRef(createIntMsg(message))
                storeRef(MessageRelaxed.tlbCodec(AnyTlbConstructor), intMsg)
            }
        }
        return latestCell
    }

    // TODO: implement 'addExtension' and 'removeExtension' actions
    private fun storeOutListExtended(messages: Array<out WalletTransfer>): CellSlice {
        return CellBuilder.createCell {
            storeBit(false)
            storeRef(storeOutList(messages))
        }.beginParse()
    }

    override fun createTransferUnsignedBody(
        validUntil: Long,
        seqno: Int,
        messageType: MessageType,
        queryId: BigInt?,
        vararg gifts: WalletTransfer
    ): Cell {
        if (gifts.size > 255) {
            throw IllegalArgumentException("Maximum number of messages in a single transfer is 255")
        }

        val opCode = if (messageType === MessageType.Internal) OpCodes.AuthSignedInternal.code else OpCodes.AuthSigned.code

        return CellBuilder.createCell {

            storeUInt(opCode, 32)
            storeSlice(storeWalletId())

            if (seqno == 0) {
                for (i in 0 until 32) {
                    storeBit(true)
                }
            } else {
                storeUInt(validUntil, 32)
            }
            storeUInt(seqno, 32)
            storeSlice(storeOutListExtended(gifts))
        }
    }

    companion object {
        enum class OpCodes(val code: Long) {
            ActionSendMsg(0x0ec3c86d),
            ActionSetCode(0xad4de08e),
            ActionExtendedSetData(0x1ff8ea0b),
            ActionExtendedAddExtension(0x1c40db9f),
            ActionExtendedRemoveExtension(0x5eaef4a4),
            ActionExtendedSetSignatureAuthAllowed(0x20cbb95a),
            AuthExtension(0x6578746e),
            AuthSigned(0x7369676e),
            AuthSignedInternal(0x73696e74)
        }

        @JvmField
        val CODE =
            BagOfCells(base64("te6cckEBAQEAIwAIQgLkzzsvTG1qYeoPK1RH0mZ4WyavNjfbLe7mvNGqgm80Eg3NjhE=")).first()
    }

}