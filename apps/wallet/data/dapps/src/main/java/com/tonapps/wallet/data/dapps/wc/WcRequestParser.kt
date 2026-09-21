package com.tonapps.wallet.data.dapps.wc

import androidx.core.net.toUri
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmAction
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.blockchain.model.DappConnectRequest
import com.tonapps.blockchain.utils.hexToBigInteger
import com.tonapps.chainkit.chain.ethereum.utils.EvmCall
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.account.TokenType
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.wc.WcRouteAdapter
import com.tonapps.wc.chains.WcRequestData
import com.tonapps.wc.chains.ethereum.WcEthSignMessage
import com.tonapps.wc.chains.ethereum.WcEthTransaction
import com.tonapps.wc.chains.tron.WcTronSignMessage
import com.tonapps.wc.chains.tron.WcTronSignTransaction
import com.tonapps.wc.models.WcConnection
import com.tonapps.wc.models.WcDappConnection
import com.tonapps.wc.models.WcSessionProposal
import com.tonapps.wc.models.WcValidation
import java.math.BigInteger

class WcRequestParser : WcRouteAdapter {

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
            domainStatus = when (validation) {
                WcValidation.Scam -> DappConnectRequest.DomainStatus.Scam
                WcValidation.Valid -> DappConnectRequest.DomainStatus.Valid
                WcValidation.Invalid -> DappConnectRequest.DomainStatus.Invalid
                WcValidation.Unknown -> DappConnectRequest.DomainStatus.Unknown
            },
            source = wcConnection.toDappSource(),
            chains = proposal.chains.map { it.value }
        )
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
    ): ConfirmRequest {
        return ConfirmRequest(
            data = CommonTransactionData(
                walletId = walletId,
                assetId = chain.coinAssetId,
            ),
            action = ConfirmAction.Sign,
            type = ConfirmType.Message(
                data = payload,
                type = when (type) {
                    WcEthSignMessage.WcSignType.Message -> ConfirmType.Message.Type.Legacy
                    WcEthSignMessage.WcSignType.PersonalMessage -> ConfirmType.Message.Type.Personal
                    WcEthSignMessage.WcSignType.TypedMessage -> ConfirmType.Message.Type.Typed
                    WcEthSignMessage.WcSignType.Default -> ConfirmType.Message.Type.Default
                    WcEthSignMessage.WcSignType.Transaction -> ConfirmType.Message.Type.Transaction
                }
            ),
            context = ConfirmContext.Dapp(
                requestId = id,
                topic = meta.topic,
                url = meta.app.url.toUri().host.orEmpty(),
                name = meta.app.name,
            ),
        )
    }

    private fun handleEthTransactionRequest(
        id: String,
        chain: Chain,
        payload: WcEthTransaction,
        meta: WcDappConnection,
        walletId: String,
    ): ConfirmRequest {
        val nonce = payload.nonce.hexToBigInteger().longValue()
        val transfer = EvmCall.decodeTransfer(payload.data)

        return ConfirmRequest(
            data = CommonTransactionData(
                assetId = when {
                    transfer != null -> chain.buildTokenAssetId(
                        contract = payload.to ?: throw IllegalArgumentException("Contract calls require a 'to' address")
                    )
                    else -> chain.coinAssetId
                },
                walletId = walletId,
                nonce = nonce,
                fee = getEthFee(chain, payload),
            ),
            action = when (payload.isSend) {
                true -> ConfirmAction.SignAndSend
                false -> ConfirmAction.Sign
            },
            type = when {
                transfer != null -> ConfirmType.Transfer(
                    to = transfer.to,
                    amount = transfer.amount,
                )
                payload.data == "0x" -> ConfirmType.Transfer(
                    to = payload.to ?: throw IllegalArgumentException("Contract calls require a 'to' address"),
                    amount = payload.value.hexToBigInteger(),
                )
                else -> ConfirmType.Call(
                    contract = payload.to.orEmpty(),
                    data = payload.data,
                    amount = payload.value.hexToBigInteger(),
                )
            },
            context = ConfirmContext.Dapp(
                requestId = id,
                topic = meta.topic,
                url = meta.app.url.toUri().host.orEmpty(),
                name = meta.app.name,
            ),
        )
    }

    private fun getEthFee(chian: Chain, payload: WcEthTransaction): Fee? {
        val gasLimit = payload.gasLimit.hexToBigInteger()
        val gasPrice = payload.gasPrice.hexToBigInteger()

        return if (chian.supportsEip1559()) {
            Fee.Eip1559(
                limit = gasLimit,
                networkPrice = gasPrice,
                minerPrice = gasPrice,
                maxPrice = gasPrice,
                amount = gasPrice.multiply(gasLimit)
            )
        } else if (gasLimit != BigInteger.ZERO || gasPrice != BigInteger.ZERO) {
            Fee.Gas(
                limit = gasLimit,
                price = gasPrice,
                amount = gasPrice.multiply(gasLimit),
            )
        } else {
            null
        }
    }

    // TODO
    private val Chain.mainTokenType: TokenType get() {
        return when (network.type) {
            Network.Type.Arbitrum,
            Network.Type.Base,
            Network.Type.Ethereum -> TokenType.Erc20
            Network.Type.Bitcoin -> TokenType.Erc20
            Network.Type.Smartchain -> TokenType.Brc20
            Network.Type.Tron -> TokenType.Trc20
            Network.Type.Ton -> TokenType.Jetton
        }
    }

    private fun Chain.buildTokenAssetId(contract: String): String {
        return "${network.type.id}/${network.group.id}/${mainTokenType.id}/${contract}"
    }

    fun Chain.supportsEip1559(): Boolean =
        when (this) {
            is Chain.Arbitrum,
            is Chain.Base,
            is Chain.Ethereum -> true

            else -> false
        }
}
