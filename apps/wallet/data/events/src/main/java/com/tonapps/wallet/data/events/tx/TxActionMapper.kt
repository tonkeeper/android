package com.tonapps.wallet.data.events.tx

import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.BlockchainAddress
import com.tonapps.wallet.api.entity.value.Timestamp
import com.tonapps.wallet.api.tron.entity.TronEventEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.events.ActionType
import com.tonapps.wallet.data.events.getTonAmountRaw
import com.tonapps.wallet.data.events.isOutTransfer
import com.tonapps.wallet.data.events.tx.model.TxAction
import com.tonapps.wallet.data.events.tx.model.TxActionBody
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.data.events.tx.model.TxFlag
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.staking.StakingPool
import io.tonapi.models.AccountAddress
import io.tonapi.models.AccountEvent
import io.tonapi.models.Action
import io.tonapi.models.ActionSimplePreview
import io.tonapi.models.AddExtensionAction
import io.tonapi.models.AuctionBidAction
import io.tonapi.models.ContractDeployAction
import io.tonapi.models.CurrencyType
import io.tonapi.models.DepositStakeAction
import io.tonapi.models.DepositTokenStakeAction
import io.tonapi.models.DomainRenewAction
import io.tonapi.models.EncryptedComment
import io.tonapi.models.GasRelayAction
import io.tonapi.models.JettonBurnAction
import io.tonapi.models.JettonMintAction
import io.tonapi.models.JettonPreview
import io.tonapi.models.JettonSwapAction
import io.tonapi.models.JettonTransferAction
import io.tonapi.models.JettonVerificationType
import io.tonapi.models.NftItem
import io.tonapi.models.NftItemTransferAction
import io.tonapi.models.NftPurchaseAction
import io.tonapi.models.Price
import io.tonapi.models.PurchaseAction
import io.tonapi.models.RemoveExtensionAction
import io.tonapi.models.SetSignatureAllowedAction
import io.tonapi.models.SmartContractAction
import io.tonapi.models.SubscriptionAction
import io.tonapi.models.TonTransferAction
import io.tonapi.models.TrustType
import io.tonapi.models.UnSubscriptionAction
import io.tonapi.models.WithdrawStakeAction
import io.tonapi.models.WithdrawStakeRequestAction
import io.tonapi.models.WithdrawTokenStakeRequestAction
import android.net.Uri
import com.tonapps.blockchain.ton.extensions.toRawAddress
import kotlin.math.abs

private fun JettonPreview.toTokenEntity() = TokenEntity(
    blockchain = Blockchain.TON,
    address = address.toRawAddress(),
    name = name,
    symbol = symbol,
    imageUri = Uri.parse(image),
    decimals = decimals,
    verification = when (verification) {
        JettonVerificationType.whitelist -> TokenEntity.Verification.whitelist
        JettonVerificationType.blacklist -> TokenEntity.Verification.blacklist
        else -> TokenEntity.Verification.none
    },
    isRequestMinting = false,
    isTransferable = true,
    customPayloadApiUri = customPayloadApiUri,
    numerator = scaledUi?.numerator?.toBigDecimal(),
    denominator = scaledUi?.denominator?.toBigDecimal()
)

internal class TxActionMapper(
    private val collectiblesRepository: CollectiblesRepository,
    private val ratesRepository: RatesRepository,
    private val api: API,
) {

    fun tronEvents(address: BlockchainAddress, events: List<TronEventEntity>): List<TxEvent> {
        return events.mapNotNull { event ->
            tronEvent(address, event)
        }
    }

    fun tronEvent(address: BlockchainAddress, event: TronEventEntity): TxEvent? {
        val currency = WalletCurrency.USDT_TRON
        val isOutgoing = event.from == address.value
        val builder = TxActionBody.Builder(if (isOutgoing) ActionType.Send else ActionType.Received)
        val isTestnet = address.network.isTestnet
        builder.setRecipient(TxActionBody.Account(
            address = event.to,
            testnet = isTestnet
        ))
        builder.setSender(TxActionBody.Account(
            address = event.from,
            testnet = isTestnet
        ))
        val isScam = event.from != address.value && event.amount < Coins.of(0.1, currency.decimals)
        if (isOutgoing) {
            builder.setOutgoingAmount(event.amount, currency)
        } else {
            builder.setIncomingAmount(event.amount, currency)
        }
        val action = TxAction(
            body = builder.build(),
            status = if (event.isFailed) TxAction.Status.Failed else TxAction.Status.Ok,
            isMaybeSpam = false
        )

        val extra = event.batteryCharges?.let {
            TxEvent.Extra.Battery(it)
        } ?: TxEvent.Extra.Battery()

        return TxEvent(
            hash = event.transactionHash,
            lt = event.timestamp.value,
            timestamp = event.timestamp,
            actions = listOf(action),
            isScam = isScam,
            inProgress = event.inProgress,
            progress = .5f,
            blockchain = Blockchain.TRON,
            extra = extra
        )
    }

    suspend fun events(address: BlockchainAddress, events: List<AccountEvent>) = events.map { event ->
        event(address, event)
    }

    suspend fun event(address: BlockchainAddress, event: AccountEvent): TxEvent {
        val actions = event.actions.map { action ->
            action(address, action)
        }
        val extra = if (event.extra > 0) {
            TxEvent.Extra.Refund(Coins.of(event.extra))
        } else {
            TxEvent.Extra.Fee(Coins.of(abs(event.extra)))
        }
        return TxEvent(
            hash = event.eventId,
            lt = event.lt,
            timestamp = Timestamp.from(event.timestamp),
            actions = actions,
            isScam = event.isScam,
            inProgress = event.inProgress,
            progress = event.progress,
            blockchain = Blockchain.TON,
            extra = extra
        )
    }

    private fun account(account: AccountAddress, testnet: Boolean) = TxActionBody.Account(
        address = account.address,
        isScam = account.isScam,
        isWallet = account.isWallet,
        name = account.name,
        icon = account.icon,
        testnet = testnet
    )

    private fun currencySimple(price: Price) = WalletCurrency.simple(
        code = price.tokenName,
        decimals = price.decimals,
        name = price.tokenName,
        imageUrl = price.image
    )

    private fun fetchNft(address: BlockchainAddress, nftAddress: String) = collectiblesRepository.getNft(
        accountId = address.value,
        network = address.network,
        address = nftAddress
    )

    private fun product(product: NftEntity) = TxActionBody.Product(
        id = product.address,
        type = TxActionBody.Product.Type.Nft,
        title = product.name,
        subtitle = product.collectionName,
        imageUrl = product.thumbUri.toString()
    )

    private fun product(nft: NftItem, network: TonNetwork) = product(NftEntity(nft, network))

    private fun text(comment: String?, encryptedComment: EncryptedComment?): TxActionBody.Text? {
        if (comment != null) {
            val text = comment.trim()
            if (text.isBlank()) {
                return null
            }
            return TxActionBody.Text.Plain(text)
        }
        if (encryptedComment != null) {
            return TxActionBody.Text.Encrypted(encryptedComment.encryptionType, encryptedComment.cipherText)
        }
        return null
    }

    private fun currency(jettonPreview: JettonPreview): WalletCurrency {
        val chain = WalletCurrency.Chain.TON(
            address = jettonPreview.address,
            decimals = jettonPreview.decimals,
        )
        return WalletCurrency(
            code = jettonPreview.symbol,
            title = jettonPreview.name,
            chain = chain,
            iconUrl = jettonPreview.image
        )
    }

    private fun currency(price: Price): WalletCurrency {
        return when (price.currencyType) {
            CurrencyType.fiat -> {
                return WalletCurrency.of(price.tokenName) ?: currencySimple(price)
            }
            CurrencyType.native -> WalletCurrency.TON
            CurrencyType.jetton -> {
                val jetton = price.jetton ?: return currencySimple(price)
                val chain = WalletCurrency.createChain("jetton", jetton)
                return WalletCurrency(
                    code = price.tokenName,
                    title = price.tokenName,
                    chain = chain,
                    iconUrl = price.image
                )
            }
            else -> currencySimple(price)
        }
    }

    private suspend fun isMaybeSpam(address: BlockchainAddress, action: Action): Boolean {
        val isTransfer = action.type == Action.Type.TonTransfer || action.type == Action.Type.JettonTransfer
        if (isTransfer && !action.isOutTransfer(address.value)) {
            val total = action.getTonAmountRaw(address.network, ratesRepository)
            return total < api.getConfig(address.network).reportAmount
        } else {
            return false
        }
    }

    suspend fun action(address: BlockchainAddress, action: Action): TxAction {
        val status = when(action.status) {
            Action.Status.ok -> TxAction.Status.Ok
            Action.Status.failed -> TxAction.Status.Failed
            else -> TxAction.Status.Unknown
        }
        val body = actionBody(address, action)
        return TxAction(
            body = body,
            status = status,
            isMaybeSpam = isMaybeSpam(address, action)
        )
    }

    fun actionBody(address: BlockchainAddress, action: Action): TxActionBody {
        if (action.gasRelay != null) {
            return gasRelay(address,action.gasRelay!!)
        }
        if (action.purchase != null) {
            return purchase(address,action.purchase!!)
        }
        if (action.jettonSwap != null) {
            return jettonSwap(address,action.jettonSwap!!)
        }
        if (action.jettonTransfer != null) {
            return jettonTransfer(address,action.jettonTransfer!!)
        }
        if (action.tonTransfer != null) {
            return tonTransfer(address,action.tonTransfer!!)
        }
        if (action.smartContractExec != null) {
            return smartContract(address,action.smartContractExec!!)
        }
        if (action.nftItemTransfer != null) {
            return nftItemTransfer(address,action.nftItemTransfer!!)
        }
        if (action.contractDeploy != null) {
            return contractDeploy(action.contractDeploy!!)
        }
        if (action.depositStake != null) {
            return depositStake(address,action.depositStake!!)
        }
        if (action.jettonMint != null) {
            return jettonMint(address,action.jettonMint!!)
        }
        if (action.withdrawStakeRequest != null) {
            return withdrawStakeRequest(address, action.withdrawStakeRequest!!)
        }
        if (action.domainRenew != null) {
            return domainRenew(action.domainRenew!!)
        }
        if (action.auctionBid != null) {
            return auctionBid(address,action.auctionBid!!)
        }
        if (action.withdrawStake != null) {
            return withdrawStake(address,action.withdrawStake!!)
        }
        if (action.nftPurchase != null) {
            return nftPurchase(address,action.nftPurchase!!)
        }
        if (action.jettonBurn != null) {
            return jettonBurn(action.jettonBurn!!)
        }
        if (action.unSubscribe != null) {
            return unSubscribe(address,action.unSubscribe!!)
        }
        if (action.subscribe != null) {
            return subscribe(address,action.subscribe!!)
        }
        if (action.depositTokenStake != null) {
            return depositTokenStake(action.depositTokenStake!!)
        }
        if (action.withdrawTokenStakeRequest != null) {
            return withdrawTokenStakeRequest(action.withdrawTokenStakeRequest!!)
        }
        if (action.removeExtension != null) {
            return removeExtension(address, action.removeExtension!!)
        }
        if (action.addExtension != null) {
            return addExtension(address, action.addExtension!!)
        }
        if (action.setSignatureAllowedAction != null) {
            return setSignatureAllowed(address, action.setSignatureAllowedAction!!)
        }
        return simplePreview(address, action.simplePreview)
    }

    private fun removeExtension(address: BlockchainAddress, action: RemoveExtensionAction): TxActionBody {
        val builder = TxActionBody.Builder(ActionType.RemoveExtension)
        builder.setSubtitle(action.extension)
        builder.setSender(account(action.wallet, address.network.isTestnet))
        return builder.build()
    }

    private fun addExtension(address: BlockchainAddress, action: AddExtensionAction): TxActionBody {
        val builder = TxActionBody.Builder(ActionType.AddExtension)
        builder.setSubtitle(action.extension)
        builder.setSender(account(action.wallet, address.network.isTestnet))
        return builder.build()
    }

    private fun setSignatureAllowed(address: BlockchainAddress, action: SetSignatureAllowedAction): TxActionBody {
        val type = if (action.allowed) ActionType.SetSignatureAllowed else ActionType.SetSignatureNotAllowed
        val builder = TxActionBody.Builder(type)
        builder.setSender(account(action.wallet, address.network.isTestnet))
        return builder.build()
    }

    private fun subscribe(address: BlockchainAddress, action: SubscriptionAction): TxActionBody {
        val amount = Coins.ofNano(action.price.value, action.price.decimals)
        val builder = TxActionBody.Builder(ActionType.Subscribe)
        builder.setRecipient(account(action.beneficiary, address.network.isTestnet))
        builder.setSubtitle(action.subscription)
        builder.setOutgoingAmount(amount)
        builder.setImageUrl(action.beneficiary.icon)
        return builder.build()
    }

    private fun unSubscribe(address: BlockchainAddress, action: UnSubscriptionAction): TxActionBody {
        val builder = TxActionBody.Builder(ActionType.UnSubscribe)
        builder.setRecipient(account(action.beneficiary, address.network.isTestnet))
        builder.setSubtitle(action.subscription)
        builder.setImageUrl(action.beneficiary.icon)
        return builder.build()
    }

    private fun jettonBurn(action: JettonBurnAction): TxActionBody {
        val currency = currency(action.jetton)
        val amount = Coins.ofNano(action.amount, currency.decimals)
        val builder = TxActionBody.Builder(ActionType.JettonBurn)
        builder.setOutgoingAmount(amount, currency)
        if (action.jetton.verification != JettonVerificationType.whitelist) {
            builder.addFlag(TxFlag.UnverifiedToken)
        }
        return builder.build()
    }

    private fun nftPurchase(address: BlockchainAddress, action: NftPurchaseAction): TxActionBody {
        val currency = currency(action.amount)
        val amount = Coins.ofNano(action.amount.value, currency.decimals)
        val recipient = account(action.seller, address.network.isTestnet)
        val product = product(action.nft, address.network)

        val builder = TxActionBody.Builder(ActionType.NftPurchase)
        builder.setRecipient(recipient)
        builder.setOutgoingAmount(amount, currency)
        builder.setProduct(product)
        if (!action.nft.verified) {
            builder.addFlag(TxFlag.UnverifiedNft)
        } else {
            builder.addFlag(TxFlag.VerifiedNft)
        }
        return builder.build()
    }

    private fun withdrawStake(address: BlockchainAddress, action: WithdrawStakeAction): TxActionBody {
        val amount = Coins.of(action.amount)
        val recipient = account(action.pool, address.network.isTestnet)
        val builder = TxActionBody.Builder(ActionType.WithdrawStake)
        builder.setRecipient(recipient)
        builder.setOutgoingAmount(amount)
        return builder.build()
    }

    private fun auctionBid(address: BlockchainAddress, action: AuctionBidAction): TxActionBody {
        val currency = currency(action.amount)
        val amount = Coins.ofNano(action.amount.value, currency.decimals)
        val product = action.nft?.let {
            product(it, address.network)
        }
        val recipient = account(action.auction, address.network.isTestnet)
        val builder = TxActionBody.Builder(ActionType.AuctionBid)
        builder.setRecipient(recipient)
        builder.setOutgoingAmount(amount, currency)
        builder.setProduct(product)
        if (action.nft?.verified == false) {
            builder.addFlag(TxFlag.UnverifiedNft)
        } else if (action.nft?.verified == true) {
            builder.addFlag(TxFlag.VerifiedNft)
        }
        return builder.build()
    }

    private fun domainRenew(action: DomainRenewAction): TxActionBody {
        val builder = TxActionBody.Builder(ActionType.DomainRenewal)
        builder.setSubtitle(action.domain)
        return builder.build()
    }

    private fun withdrawStakeRequest(address: BlockchainAddress, action: WithdrawStakeRequestAction): TxActionBody {
        val amount = Coins.of(action.amount ?: 0L)
        val recipient = account(action.pool, address.network.isTestnet)
        val builder = TxActionBody.Builder(ActionType.WithdrawStakeRequest)
        builder.setRecipient(recipient)
        builder.setOutgoingAmount(amount)
        return builder.build()
    }

    private fun jettonMint(address: BlockchainAddress, action: JettonMintAction): TxActionBody {
        val amount = Coins.ofNano(action.amount, action.jetton.decimals)
        val currency = currency(action.jetton)
        val recipient = account(action.recipient, address.network.isTestnet)
        val builder = TxActionBody.Builder(ActionType.JettonMint)
        builder.setRecipient(recipient)
        builder.setOutgoingAmount(amount, currency)
        if (action.jetton.verification != JettonVerificationType.whitelist) {
            builder.addFlag(TxFlag.UnverifiedToken)
        }
        return builder.build()
    }

    private fun withdrawTokenStakeRequest(action: WithdrawTokenStakeRequestAction): TxActionBody {
        val stakeMeta = action.stakeMeta
        val ingoingAmount = stakeMeta?.let {
            val currency = currency(it)
            val amount = Coins.ofNano(it.value, it.decimals)
            TxActionBody.Value(amount, currency)
        }

        val builder = TxActionBody.Builder(ActionType.WithdrawStake)
        builder.setSubtitle(action.protocol.name)
        builder.setImageUrl(action.protocol.image)
        ingoingAmount?.let(builder::setIncomingAmount)
        return builder.build()
    }

    private fun depositTokenStake(action: DepositTokenStakeAction): TxActionBody {
        val stakeMeta = action.stakeMeta
        val outgoingAmount = stakeMeta?.let {
            val currency = currency(it)
            val amount = Coins.ofNano(it.value, it.decimals)
            TxActionBody.Value(amount, currency)
        }

        val builder = TxActionBody.Builder(ActionType.DepositStake)
        builder.setSubtitle(action.protocol.name)
        builder.setImageUrl(action.protocol.image)
        outgoingAmount?.let(builder::setOutgoingAmount)
        return builder.build()
    }

    private fun depositStake(address: BlockchainAddress, action: DepositStakeAction): TxActionBody {
        val implementation = StakingPool.implementation(action.implementation)
        // TODO fix after full move KMM
        val iconUrl = "android.resource://com.ton_keeper/${StakingPool.getIcon(implementation)}"
        val amount = Coins.of(action.amount)
        val builder = TxActionBody.Builder(ActionType.DepositStake)
        builder.setRecipient(account(action.pool, address.network.isTestnet))
        builder.setOutgoingAmount(amount)
        builder.setImageUrl(iconUrl)
        return builder.build()
    }

    private fun contractDeploy(action: ContractDeployAction) = TxActionBody.Builder(ActionType.DeployContract).build()

    private fun nftItemTransfer(address: BlockchainAddress, action: NftItemTransferAction): TxActionBody {
        val nft = fetchNft(address, action.nft)
        val product = nft?.let(::product)
        val sender = action.sender?.let { account(it, address.network.isTestnet) }
        val recipient = action.recipient?.let { account(it, address.network.isTestnet) }
        val isOutgoing = sender?.address?.equalsAddress(address.value) == true
        val builder = TxActionBody.Builder(if (isOutgoing) ActionType.NftSend else ActionType.NftReceived)
        sender?.let(builder::setSender)
        recipient?.let(builder::setRecipient)
        builder.setProduct(product)
        if (!isOutgoing) {
            builder.setImageUrl(sender?.icon)
        }
        builder.setText(text(action.comment, action.encryptedComment))
        if (nft?.verified == false) {
            builder.addFlag(TxFlag.UnverifiedNft)
        } else if (nft?.verified == true) {
            builder.addFlag(TxFlag.VerifiedNft)
        }
        return builder.build()
    }

    private fun smartContract(address: BlockchainAddress, action: SmartContractAction): TxActionBody {
        val amount = Coins.of(action.tonAttached)
        val builder = TxActionBody.Builder(ActionType.CallContract)
        builder.setSender(account(action.executor, address.network.isTestnet))
        builder.setSubtitle(action.payload ?: action.operation)
        builder.setOutgoingAmount(amount)
        return builder.build()
    }

    private fun tonTransfer(address: BlockchainAddress, action: TonTransferAction): TxActionBody {
        val amount = Coins.of(action.amount)
        val currency = WalletCurrency.TON
        val sender = account(action.sender, address.network.isTestnet)
        val recipient = account(action.recipient, address.network.isTestnet)
        val isOutgoing = sender.address.equalsAddress(address.value)
        val builder = TxActionBody.Builder(if (isOutgoing) ActionType.Send else ActionType.Received)
        builder.setSender(sender)
        builder.setRecipient(recipient)
        if (isOutgoing) {
            builder.setOutgoingAmount(amount, currency)
        } else {
            builder.setIncomingAmount(amount, currency)
            builder.setImageUrl(action.sender.icon)
        }
        builder.setText(text(action.comment, action.encryptedComment))
        return builder.build()
    }

    private fun jettonTransfer(address: BlockchainAddress, action: JettonTransferAction): TxActionBody {
        val jetton = action.jetton.toTokenEntity()
        val amount = jetton.toUIAmount(Coins.ofNano(action.amount, jetton.decimals))
        val currency = currency(action.jetton)
        val sender = action.sender?.let { account(it, address.network.isTestnet) }
        val recipient = action.recipient?.let { account(it, address.network.isTestnet) }
        val isOutgoing = recipient?.let {
            !it.address.equalsAddress(address.value)
        } ?: false
        val type = if (isOutgoing) ActionType.Send else ActionType.Received
        val builder = TxActionBody.Builder(type)
        sender?.let(builder::setSender)
        recipient?.let(builder::setRecipient)
        if (action.jetton.verification != JettonVerificationType.whitelist) {
            builder.addFlag(TxFlag.UnverifiedToken)
        }
        if (type == ActionType.Received) {
            builder.setIncomingAmount(amount, currency)
            builder.setImageUrl(sender?.icon)
        } else {
            builder.setOutgoingAmount(amount, currency)
        }
        builder.setText(text(action.comment, action.encryptedComment))
        return builder.build()
    }

    private fun jettonSwap(address: BlockchainAddress, action: JettonSwapAction): TxActionBody {
        val incomingAmount = if (action.tonIn != null) {
            TxActionBody.Value(Coins.of(action.tonIn!!), WalletCurrency.TON)
        } else {
            val jetton = action.jettonMasterIn!!.toTokenEntity()
            val currency = currency(action.jettonMasterIn!!)
            TxActionBody.Value(jetton.toUIAmount(Coins.ofNano(action.amountIn, jetton.decimals)), currency)
        }

        val outgoingAmount = if (action.tonOut != null) {
            TxActionBody.Value(Coins.of(action.tonOut!!), WalletCurrency.TON)
        } else {
            val jetton = action.jettonMasterOut!!.toTokenEntity()
            val currency = currency(action.jettonMasterOut!!)
            TxActionBody.Value(jetton.toUIAmount(Coins.ofNano(action.amountOut, jetton.decimals)), currency)
        }

        val builder = TxActionBody.Builder(ActionType.Swap)
        builder.setSubtitle(action.dex)
        builder.setIncomingAmount(outgoingAmount)
        builder.setOutgoingAmount(incomingAmount)
        builder.setRecipient(account(action.userWallet, address.network.isTestnet))
        builder.setSender(account(action.router, address.network.isTestnet))
        action.jettonMasterOut?.verification?.let { verification ->
            if (verification != JettonVerificationType.whitelist) {
                builder.addFlag(TxFlag.UnverifiedToken)
            }
        }
        action.jettonMasterIn?.verification?.let { verification ->
            if (verification != JettonVerificationType.whitelist) {
                builder.addFlag(TxFlag.UnverifiedToken)
            }
        }
        return builder.build()
    }

    private fun purchase(address: BlockchainAddress, action: PurchaseAction): TxActionBody {
        val amount = action.amount
        val coins = Coins.ofNano(amount.value, amount.decimals)
        val builder = TxActionBody.Builder(ActionType.Purchase)
        builder.setRecipient(account(action.destination, address.network.isTestnet))
        builder.setOutgoingAmount(coins, currency(amount))
        if (amount.verification != TrustType.whitelist) {
            builder.addFlag(TxFlag.UnverifiedToken)
        }
        return builder.build()
    }

    private fun gasRelay(address: BlockchainAddress, action: GasRelayAction): TxActionBody {
        val coins = Coins.of(action.amount)
        val builder = TxActionBody.Builder(ActionType.GasRelay)
        builder.setRecipient(account(action.target, address.network.isTestnet))
        builder.setIncomingAmount(coins, WalletCurrency.TON)
        return builder.build()
    }

    private fun simplePreview(address: BlockchainAddress, simplePreview: ActionSimplePreview): TxActionBody {
        val unknown = WalletCurrency.unknown(
            imageUrl = simplePreview.valueImage
        )

        val sender = simplePreview.accounts.firstOrNull {
            it.address.equalsAddress(address.value)
        }?.let { account(it, address.network.isTestnet) }

        val recipient = simplePreview.accounts.firstOrNull {
            !it.address.equalsAddress(address.value)
        }?.let { account(it, address.network.isTestnet) }

        val builder = TxActionBody.Builder(ActionType.Unknown)
        builder.setTitle(simplePreview.name)
        builder.setSubtitle(simplePreview.description)
        builder.setImageUrl(simplePreview.actionImage)
        sender?.let(builder::setSender)
        recipient?.let(builder::setRecipient)
        builder.setValue(simplePreview.value)
        builder.setOutgoingAmount(Coins.ZERO, unknown)
        return builder.build()
    }
}
