package com.tonapps.wallet.data.dapps.wc

import androidx.core.net.toUri
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmAction
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.blockchain.model.DappConnectRequest
import com.tonapps.blockchain.utils.hexToBigInteger
import com.tonapps.chainkit.chain.ethereum.utils.EvmCall
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.account.TokenType
import com.tonapps.wc.WcRouteAdapter
import com.tonapps.wc.chains.WcRequestData
import com.tonapps.wc.chains.ethereum.WcEthSignMessage
import com.tonapps.wc.chains.ethereum.WcEthTransaction
import com.tonapps.wc.chains.solana.WcSolanaSignAllTransactions
import com.tonapps.wc.chains.solana.WcSolanaSignMessage
import com.tonapps.wc.chains.solana.WcSolanaSignTransaction
import com.tonapps.wc.chains.tron.WcTronSignMessage
import com.tonapps.wc.chains.tron.WcTronSignTransaction
import com.tonapps.wc.models.WcConnection
import com.tonapps.wc.models.WcDappConnection
import com.tonapps.wc.models.WcSessionProposal
import com.tonapps.wc.models.WcValidation

class WcRequestParser : WcRouteAdapter {

    // TODO
    private val Chain.mainTokenType: TokenType get() {
        return when (network.type) {
            Network.Type.Arbitrum,
            Network.Type.Base,
            Network.Type.Robinhood,
            Network.Type.Polygon,
            Network.Type.Avalanche,
            Network.Type.Ethereum -> TokenType.Erc20
            Network.Type.Bitcoin -> TokenType.Brc20
            Network.Type.Smartchain -> TokenType.Bep20
            Network.Type.Tron -> TokenType.Trc20
            Network.Type.Ton -> TokenType.Jetton
            Network.Type.Solana -> TokenType.SPL
        }
    }

    override fun <T : WcRequestData> convertRequest(
        requestId: String,
        walletId: String,
        chain: Chain,
        meta: WcDappConnection,
        rawRequest: T,
    ): ConfirmRequest? {
        return when (rawRequest) {
            // Eth
            is WcEthTransaction -> handleEthTransactionRequest(
                id = requestId,
                chain = chain,
                payload = rawRequest,
                meta = meta,
                walletId = walletId,
            )
            is WcEthSignMessage -> toConfirmRequest(
                id = requestId,
                chain = chain,
                type = rawRequest.type,
                payload = rawRequest.data,
                meta = meta,
                walletId = walletId,
            )

            // Tron
            is WcTronSignTransaction -> toConfirmRequest( // TODO remove Transaction sign type
                id = requestId,
                chain = chain,
                type = WcEthSignMessage.WcSignType.Transaction,
                payload = rawRequest.transaction.transaction.toString(),
                meta = meta,
                walletId = walletId,
            )
            is WcTronSignMessage -> toConfirmRequest(
                id = requestId,
                chain = chain,
                type = WcEthSignMessage.WcSignType.Default,
                payload = rawRequest.message,
                meta = meta,
                walletId = walletId,
            )

            // Solana
            is WcSolanaSignMessage -> toConfirmRequest(
                id = requestId,
                chain = chain,
                type = WcEthSignMessage.WcSignType.Default,
                payload = rawRequest.message,
                meta = meta,
                walletId = walletId,
            )
            // solana_signAndSendTransaction comes as a signed transaction the wallet broadcasts.
            is WcSolanaSignTransaction -> toConfirmRequest(
                id = requestId,
                chain = chain,
                type = WcEthSignMessage.WcSignType.Transaction,
                payload = rawRequest.transaction,
                meta = meta,
                walletId = walletId,
                action = when (rawRequest.isSend) {
                    true -> ConfirmAction.SignAndSend
                    false -> ConfirmAction.Sign
                },
            )
            is WcSolanaSignAllTransactions -> toConfirmRequest(
                id = requestId,
                chain = chain,
                payload = WcSolanaAnswers.packTransactions(rawRequest.transactions),
                messageType = ConfirmType.Message.Type.Transactions,
                meta = meta,
                walletId = walletId,
            )

            else -> null
        }
    }


    override fun handleMultiSession(
        requestId: String,
        proposal: WcSessionProposal,
        wcConnection: WcConnection,
        validation: WcValidation,
    ): DappConnectRequest {
        val peer = proposal.app

        return DappConnectRequest(
            id = requestId,
            info = DappConnectRequest.Info(
                dappHost = proposal.url.toUri().host.orEmpty(),
                dappName = peer.name,
                topic = proposal.topic,
                iconUrl = peer.icon
            ),
            domainStatus = validation.toDomainStatus(),
            source = wcConnection.toDappSource(),
            chains = proposal.chains.map { it.value },
            requiredChains = proposal.chains.filter { it.isRequired }.map { it.value }.toSet(),
        )
    }

    private fun WcValidation.toDomainStatus(): DappConnectRequest.DomainStatus {
        return when (this) {
            WcValidation.Scam -> DappConnectRequest.DomainStatus.Scam
            WcValidation.Valid -> DappConnectRequest.DomainStatus.Valid
            WcValidation.Invalid -> DappConnectRequest.DomainStatus.Invalid
            WcValidation.Unknown -> DappConnectRequest.DomainStatus.Unknown
        }
    }

    private fun WcConnection.toDappSource(): DappConnectRequest.Source {
        return when (this) {
            WcConnection.Deeplink -> DappConnectRequest.Source.Browser
            WcConnection.DApp -> DappConnectRequest.Source.Dapp
            WcConnection.Qr -> DappConnectRequest.Source.Qr
        }
    }

    private fun toConfirmRequest(
        id: String,
        chain: Chain,
        payload: String,
        type: WcEthSignMessage.WcSignType,
        walletId: String,
        meta: WcDappConnection,
        action: ConfirmAction = ConfirmAction.Sign,
    ): ConfirmRequest {
        return toConfirmRequest(
            id = id,
            chain = chain,
            payload = payload,
            messageType = when (type) {
                WcEthSignMessage.WcSignType.Message -> ConfirmType.Message.Type.Legacy
                WcEthSignMessage.WcSignType.PersonalMessage -> ConfirmType.Message.Type.Personal
                WcEthSignMessage.WcSignType.TypedMessage -> ConfirmType.Message.Type.Typed
                WcEthSignMessage.WcSignType.Default -> ConfirmType.Message.Type.Default
                WcEthSignMessage.WcSignType.Transaction -> ConfirmType.Message.Type.Transaction
            },
            walletId = walletId,
            meta = meta,
            action = action,
        )
    }

    private fun toConfirmRequest(
        id: String,
        chain: Chain,
        payload: String,
        messageType: ConfirmType.Message.Type,
        walletId: String,
        meta: WcDappConnection,
        action: ConfirmAction = ConfirmAction.Sign,
    ): ConfirmRequest {
        return ConfirmRequest(
            data = CommonTransactionData(
                walletId = walletId,
                assetId = chain.coinAssetId,
            ),
            action = action,
            type = ConfirmType.Message(
                data = payload,
                type = messageType,
            ),
            context = ConfirmContext.Dapp(
                requestId = id,
                topic = meta.topic,
                url = meta.app.url.toUri().host.orEmpty(),
                name = meta.app.name,
                domainStatus = meta.validation?.toDomainStatus(),
            ),
        )
    }

    // The operation is the transaction as the dapp sent it, calldata included: chainkit signs a payload
    // as it reads, so a token transfer stays the token contract's call. The wallet prices it itself, as
    // it does every send: the gas the dapp names is left out, so the estimate still catches a revert.
    private fun handleEthTransactionRequest(
        id: String,
        chain: Chain,
        payload: WcEthTransaction,
        meta: WcDappConnection,
        walletId: String,
    ): ConfirmRequest {
        val nonce = payload.nonce.hexToBigInteger().longValue()
        val to = payload.to ?: throw IllegalArgumentException("Contract calls require a 'to' address")
        val amount = payload.value.hexToBigInteger()
        val calldata = payload.data.takeUnless { it.removePrefix("0x").isEmpty() }
        // A transfer that also moves the coin is a call: the screen shows the coin it moves, not only the token.
        val transfer = calldata?.takeIf { amount.isZero() }?.let(EvmCall::decodeTransfer)

        return ConfirmRequest(
            data = CommonTransactionData(
                assetId = when {
                    transfer != null -> chain.buildTokenAssetId(contract = to)
                    else -> chain.coinAssetId
                },
                walletId = walletId,
                nonce = nonce,
            ),
            action = when (payload.isSend) {
                true -> ConfirmAction.SignAndSend
                false -> ConfirmAction.Sign
            },
            type = when {
                calldata == null -> ConfirmType.Transfer(
                    operation = ConfirmOperations.transfer(chain = chain, to = to, amount = amount),
                )
                transfer != null -> ConfirmType.Transfer(
                    operation = ConfirmOperations.call(chain = chain, contract = to, data = calldata, amount = amount),
                    tokenTransfer = ConfirmType.Transfer.TokenTransfer(to = transfer.to, amount = transfer.amount),
                )
                else -> ConfirmType.Call(
                    operation = ConfirmOperations.call(chain = chain, contract = to, data = calldata, amount = amount),
                )
            },
            context = ConfirmContext.Dapp(
                requestId = id,
                topic = meta.topic,
                url = meta.app.url.toUri().host.orEmpty(),
                name = meta.app.name,
                domainStatus = meta.validation?.toDomainStatus(),
            ),
        )
    }

    // As Chain.tokenAssetId in features/core, which the backend keys the wallet's assets by.
    private fun Chain.buildTokenAssetId(contract: String): String {
        return "${network.type.id}/${network.mode.id}/${mainTokenType.id}/${contract.lowercase()}"
    }
}

