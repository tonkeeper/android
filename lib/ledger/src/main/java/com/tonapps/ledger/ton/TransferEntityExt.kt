package com.tonapps.ledger.ton

import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.model.legacy.toGrams
import com.tonapps.icu.Coins
import org.ton.block.AddrStd

fun TransferEntity.getLedgerTransaction(jettonTransferAmount: Coins): Transaction? {
        if (wallet.type != WalletType.Ledger) {
            return null
        }
        
        val builder = TransactionBuilder()
        if (isNft) {
            builder.setCoins(jettonTransferAmount.toGrams())
            builder.setDestination(AddrStd.parse(nftAddress!!))
            builder.setPayload(
                TonPayloadFormat.NftTransfer(
                    queryId = queryId,
                    newOwnerAddress = destination,
                    excessesAddress = contract.address,
                    forwardPayload = getCommentForwardPayload(),
                    forwardAmount = org.ton.block.Coins.ofNano(1L),
                    customPayload = null
                )
            )
        } else if (!isTon) {
            builder.setCoins(jettonTransferAmount.toGrams())
            builder.setDestination(AddrStd.parse(token.walletAddress))
            builder.setPayload(
                TonPayloadFormat.JettonTransfer(
                    queryId = queryId,
                    coins = coins,
                    receiverAddress = destination,
                    excessesAddress = contract.address,
                    forwardPayload = getCommentForwardPayload(),
                    forwardAmount = org.ton.block.Coins.ofNano(1L),
                    customPayload = null
                )
            )
        } else {
            builder.setCoins(coins)
            builder.setDestination(destination)
            comment?.let {
                builder.setPayload(TonPayloadFormat.Comment(it))
            }
        }
        builder.setSendMode(sendMode)
        builder.setSeqno(seqno)
        builder.setTimeout(validUntil.toInt())
        builder.setBounceable(bounceable)
        builder.setStateInit(stateInitRef)
        return builder.build()
    }