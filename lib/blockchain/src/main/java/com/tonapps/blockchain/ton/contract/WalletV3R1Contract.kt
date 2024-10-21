package com.tonapps.blockchain.ton.contract

import com.tonapps.blockchain.ton.extensions.parseCell
import org.ton.api.pub.PublicKeyEd25519
import org.ton.bigint.BigInt
import org.ton.block.MessageRelaxed
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.WalletTransfer
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeRef

open class WalletV3R1Contract(
    workchain: Int = DEFAULT_WORKCHAIN,
    publicKey: PublicKeyEd25519,
) : BaseWalletContract(workchain, publicKey) {

    override val features: WalletFeature = WalletFeature.NONE

    override val maxMessages: Int = 4

    override fun getWalletVersion() = WalletVersion.V3R1

    override fun getStateCell(): Cell {
        return CellBuilder.createCell {
            storeUInt(0, 32)
            storeUInt(walletId, 32)
            storeBytes(publicKey.key.toByteArray())
        }
    }

    override fun getCode(): Cell {
        return CODE
    }

    override fun createTransferUnsignedBody(
        validUntil: Long,
        seqNo: Int,
        internalMessage: Boolean,
        queryId: BigInt?,
        vararg gifts: WalletTransfer
    ) = CellBuilder.createCell {
        if (gifts.size > maxMessages) {
            throw IllegalArgumentException("Maximum number of messages in a single transfer is $maxMessages")
        }

        storeUInt(walletId, 32)
        storeUInt(validUntil, 32)
        storeUInt(seqNo, 32)
        for (gift in gifts) {
            var sendMode = 3
            if (gift.sendMode > -1) {
                sendMode = gift.sendMode
            }
            val intMsg = CellRef(createIntMsg(gift))

            storeUInt(sendMode, 8)
            storeRef(MessageRelaxed.tlbCodec(AnyTlbConstructor), intMsg)
        }
    }

    companion object {
        @JvmField
        val CODE = "te6cckEBAQEAYgAAwP8AIN0gggFMl7qXMO1E0NcLH+Ck8mCDCNcYINMf0x/TH/gjE7vyY+1E0NMf0x/T/9FRMrryoVFEuvKiBPkBVBBV+RDyo/gAkyDXSpbTB9QC+wDo0QGkyMsfyx/L/8ntVD++buA=".parseCell()

    }
}
