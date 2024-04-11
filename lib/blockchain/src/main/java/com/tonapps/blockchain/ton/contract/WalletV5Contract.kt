package com.tonapps.blockchain.ton.contract

import org.ton.api.pub.PublicKeyEd25519
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.WalletTransfer
import org.ton.crypto.base64

class WalletV5Contract(
    workchain: Int = DEFAULT_WORKCHAIN,
    publicKey: PublicKeyEd25519,
) : BaseWalletContract(workchain, publicKey) {

    override fun getStateCell(): Cell {
        return CellBuilder.createCell {
            storeUInt(0, 32)
            storeUInt(walletId, 32)
            storeBits(publicKey.key)
            storeBit(false)
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
        throw NotImplementedError("Method is not implemented")
    }

    companion object {
        @JvmField
        val CODE =
            BagOfCells(base64("te6cckEBAQEAIwAIQgLkzzsvTG1qYeoPK1RH0mZ4WyavNjfbLe7mvNGqgm80Eg3NjhE=")).first()
    }

}