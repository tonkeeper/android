package com.tonapps.tonkeeper.ui.screen.send.main.helper

import com.tonapps.blockchain.ton.ONE_TON
import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import io.tonapi.models.MessageConsequences
import org.ton.bitstring.BitString
import org.ton.block.MsgAddressInt
import org.ton.cell.Cell
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.tlb.CellRef
import java.math.BigInteger

object SendNftHelper {

    suspend fun totalAmount(
        api: API,
        wallet: WalletEntity,
        nftAddress: MsgAddressInt,
        seqno: Int,
        destination: MsgAddressInt,
        validUntil: Long,
        comment: Any?,
    ): Coins {
        val consequences = simpleEmulate(api, wallet, nftAddress, seqno, destination, validUntil, comment)
        val extra = Coins.of(consequences?.event?.extra ?: 0)
        val isRefund = !extra.isNegative
        return if (isRefund) {
            Coins.of(0.05)
        } else {
            extra.abs() + Coins.of(0.05)
        }
    }

    private suspend fun simpleEmulate(
        api: API,
        wallet: WalletEntity,
        nftAddress: MsgAddressInt,
        seqno: Int,
        destination: MsgAddressInt,
        validUntil: Long,
        comment: Any?,
    ): MessageConsequences? {
        val contract = wallet.contract
        val cell = simpleBoc(
            contract = contract,
            nftAddress = nftAddress,
            seqno = seqno,
            destination = destination,
            validUntil = validUntil,
            comment = comment,
        )

        val signature = BitString(EmptyPrivateKeyEd25519.sign(cell.hash()))
        val signedBody = contract.signedBody(signature, cell)
        val boc = contract.createTransferMessageCell(contract.address, seqno, signedBody)

        return api.emulate(
            cell = boc,
            testnet = wallet.testnet,
            address = wallet.address,
            balance = (Coins.ONE + Coins.ONE).toLong() // Emulate with higher balance to calculate fair amount to send
        )
    }

    private fun simpleBoc(
        contract: BaseWalletContract,
        nftAddress: MsgAddressInt,
        seqno: Int,
        destination: MsgAddressInt,
        validUntil: Long,
        comment: Any?,
    ): Cell {
        val queryId = TransferEntity.newWalletQueryId()
        val gift = simpleGift(contract, nftAddress, seqno, destination, queryId, comment)
        return contract.createTransferUnsignedBody(
            validUntil = validUntil,
            seqNo = seqno,
            queryId = queryId,
            gifts = arrayOf(gift)
        )
    }

    private fun simpleGift(
        contract: BaseWalletContract,
        nftAddress: MsgAddressInt,
        seqno: Int,
        destination: MsgAddressInt,
        queryId: BigInteger,
        comment: Any?,
    ): WalletTransfer {

        val body = TonTransferHelper.nft(
            newOwnerAddress = destination,
            excessesAddress = contract.address,
            queryId = queryId,
            body = comment,
        )
        
        val builder = WalletTransferBuilder()
        builder.bounceable = true
        builder.sendMode = 3
        builder.coins = ONE_TON
        builder.messageData = MessageData.raw(body, contract.stateInitRef)
        builder.destination = nftAddress
        return builder.build()
    }
}