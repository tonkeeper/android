package com.tonapps.blockchain.ton.contract

import org.ton.api.pub.PublicKeyEd25519
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.WalletTransfer
import org.ton.crypto.base64

class WalletV5R1Contract(
    workchain: Int = DEFAULT_WORKCHAIN,
    publicKey: PublicKeyEd25519,
    private val networkGlobalId: Int = -239,
    private val subwalletNumber: Int = 0
) : BaseWalletContract(workchain, publicKey) {

    override fun getStateCell(): Cell {
        return CellBuilder.createCell {
            storeInt(0, 33)
            storeRef(storeWalletId())
            storeBytes(publicKey.key.toByteArray(), 32)
            storeBit(false)
        }
    }

    private fun storeWalletId(): Cell {
        return CellBuilder.createCell {
            storeInt(networkGlobalId, 32)
            storeInt(workchain, 8)
            storeUInt(0, 8) // "v5" to 0
            storeUInt(subwalletNumber, 32)
        }
    }

    override fun getCode(): Cell {
        return CODE
    }

    override fun createTransferUnsignedBody(
        validUntil: Long,
        seqno: Int,
        vararg gifts: WalletTransfer
    ): Cell {
        throw NotImplementedError("Not implemented")
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