package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.contract.WalletFeature
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.tron.TronTransfer
import com.tonapps.chainkit.chain.ethereum.utils.Hermes
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.account.TokenType
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.core.components.tokenAssetId
import com.tonapps.icu.Coins
import com.tonapps.log.L
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.coinValue
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.api.entity.EmulateWithBatteryResult
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.account.tonCoinAssetId
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import com.tonapps.wallet.data.token.TokenRepository
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import org.ton.cell.Cell

/**
 * Prices the ways a multichain transfer can pay its fee. Battery is the only relayed method: paying a
 * transaction's fee with another chain's coin is not offered, so every other option is settled on the
 * transaction's own chain.
 */
class TxFeeBuilder(
    private val api: API,
    private val batteryRepository: BatteryRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val accountRepo: McAccountRepository,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val sender: GaslessSender,
    private val gaslessRelayer: GaslessRelayer,
) {

    suspend fun build(
        account: AccountWithDetails,
        energy: AccountWithDetails,
        walletId: String,
        amount: BigInteger,
        to: String,
        comment: String?,
        estimated: Fee?,
        shrinkToFit: Boolean,
    ): TxFeeState = withContext(Async.Io) {
        val native = TxFeeState(energy = energy, estimated = estimated)
        val asset = account.asset.value

        if (batteryDisabled()) {
            return@withContext native
        }
        if (asset.chain.network.mode != Network.Mode.Mainnet) {
            return@withContext native
        }
        val wallet = mcWallet(walletId) ?: return@withContext native

        if (asset.chain.isEvm || asset.chain is Chain.Solana) {
            return@withContext buildRelayed(
                wallet = wallet,
                account = account,
                energy = energy,
                call = if (asset.chain is Chain.Solana) {
                    GaslessRelayer.solanaTransfer(account, to, amount, comment)
                } else {
                    GaslessRelayer.transfer(account, to, amount)
                },
                amount = amount,
                estimated = estimated,
                nativeAmount = if (shrinkToFit) {
                    BigInteger.ZERO
                } else {
                    amount
                },
            )
        }

        if (asset !is Asset.Token) {
            return@withContext native
        }

        when (asset.type) {
            TokenType.Trc20 -> {
                if (asset.contract.display != TokenEntity.TRC20_USDT) {
                    return@withContext native
                }
                buildTronUsdt(
                    wallet = wallet,
                    tronAddress = account.data.displayAddress,
                    energy = energy,
                    amount = amount,
                    to = to,
                    estimated = estimated,
                )
            }

            TokenType.Jetton -> if (wallet.isMultichainLedger) {
                native
            } else {
                buildTonJetton(
                    wallet = wallet,
                    master = asset.contract.display,
                    account = account,
                    energy = energy,
                    amount = amount,
                    to = to,
                    comment = comment,
                    estimated = estimated,
                )
            }

            else -> native
        }
    }

    suspend fun buildSwap(
        account: AccountWithDetails,
        energy: AccountWithDetails,
        walletId: String,
        transactions: List<Transaction.Swap>,
        approvals: List<Transaction>,
        estimated: Fee?,
        attachedEnergy: BigInteger,
        shrinkToFit: Boolean,
        // What the swap takes of the account's asset, where its messages do not say: null reads it off them.
        sold: BigInteger? = null,
        // Whether a relayer may carry the approvals with the swap, as one batch. False where they are
        // sent ahead of the swap on their own, which a batch would then repeat.
        relayApprovals: Boolean = false,
    ): TxFeeState = withContext(Async.Io) {
        val native = TxFeeState(energy = energy, estimated = estimated, attachedEnergy = attachedEnergy)
        val transaction = transactions.first()
        val asset = account.asset.value
        val chain = asset.chain

        if (batteryDisabled() || (!chain.isTvm && chain !is Chain.Tron && !chain.isEvm && chain !is Chain.Solana)) {
            return@withContext native
        }

        if (chain.network.mode != Network.Mode.Mainnet) {
            return@withContext native
        }

        val wallet = mcWallet(walletId) ?: return@withContext native
        if (api.getConfig(wallet.network).batterySendDisabled) {
            return@withContext native
        }

        if (wallet.isMultichainLedger && chain.isTvm) {
            return@withContext native
        }

        when {
            // The battery is charged for the TON that leaves the wallet, and a swap that sells the TON coin
            // sends what it sells: it would be priced at the amount sold, not at the gas. A wallet that
            // sells the coin holds it for the fee, so the chain's own fee stands, as on iOS. For a `/v2`
            // route as for a `/v3` one: both are priced here.
            chain.isTvm && asset is Asset.Coin -> native

            chain.isTvm && asset is Asset.Token && transactions.size == 1 && transaction.data.isNullOrBlank() -> buildTonJetton(
                wallet = wallet,
                master = asset.contract.display,
                account = account,
                energy = energy,
                amount = transaction.amount,
                to = transaction.to.display,
                comment = null,
                estimated = estimated,
                swap = true,
            )

            chain.isTvm -> buildTonSwap(
                wallet = wallet,
                energy = energy,
                messages = transactions.map { transaction ->
                    SwapMessagePayload(
                        to = transaction.to.display,
                        amount = transaction.amount,
                        data = transaction.data,
                        stateInit = transaction.initData,
                    )
                },
                estimated = estimated,
                attachedEnergy = attachedEnergy,
            )

            chain is Chain.Tron -> buildTronSwap(wallet, account, energy, transaction, estimated)
            chain.isEvm || (chain is Chain.Solana && transactions.size == 1) ->
                buildRelayedSwap(wallet, account, energy, transaction, approvals, estimated, shrinkToFit, sold, relayApprovals)
            else -> native
        }
    }

    /**
     * The ways an operation, a `/v3` swap or a send, may be paid on a chain the gasless relayer carries.
     * The operation's [payloads] go to the relayer as they state themselves, the approve ahead of an EVM
     * swap in one batch with it: nothing is made of them that the relayer does not ask for. [sold] is what
     * the operation takes of the account's asset; with [shrinkToFit] it is prepared again to what a fee in
     * that asset leaves. [purpose] is what the relayer is told the operation is, and holds a send to.
     */
    suspend fun buildSwapOperation(
        account: AccountWithDetails,
        energy: AccountWithDetails,
        walletId: String,
        payloads: List<Payload>,
        sold: BigInteger,
        shrinkToFit: Boolean,
        estimated: Fee?,
        purpose: GaslessRelayer.Purpose = GaslessRelayer.Purpose.Swap,
    ): TxFeeState = withContext(Async.Io) {
        val native = TxFeeState(energy = energy, estimated = estimated)
        val chain = account.asset.value.chain
        val swap = payloads.lastOrNull()?.meta ?: return@withContext native

        if (batteryDisabled() || chain.network.mode != Network.Mode.Mainnet || !relayerCarries(chain)) {
            return@withContext native
        }

        // Nothing is built for a wallet the relayer is not asked to serve.
        val wallet = mcWallet(walletId) ?: return@withContext native
        if (wallet.isMultichainLedger || api.getConfig(wallet.network).batterySendDisabled) {
            return@withContext native
        }

        val sale = Sale(sold, shrinkToFit)
        when (swap) {
            is PayloadMeta.Evm.Main -> buildEvmSwap(wallet, account, energy, payloads, sale, estimated, purpose)
            is PayloadMeta.Solana if payloads.size == 1 -> buildSolanaSwap(wallet, account, energy, swap, sale, estimated, purpose)
            is PayloadMeta.Tron if payloads.size == 1 -> buildTronCall(wallet, account, energy, swap, sale, estimated, purpose)
            is PayloadMeta.Ton -> {
                // A route split into parts comes as a batch: each part goes to the relayer as its own message.
                val messages = payloads.flatMap { payload ->
                    when (val meta = payload.meta) {
                        is PayloadMeta.Ton.Main -> listOf(meta)
                        is PayloadMeta.Ton.Batch -> meta.calls.ifEmpty { throw IllegalStateException("A TON batch with no message") }
                            .map { call -> call as? PayloadMeta.Ton.Main ?: return@withContext native }
                        else -> return@withContext native
                    }
                }
                buildTonMessages(wallet, account, energy, messages, sold, estimated, purpose)
            }
            else -> native
        }
    }

    /**
     * A TON swap is its messages, one or the parts of a route split for a better price, handed to the
     * relayer as the swap states them: the relayer delivers each itself. Selling a jetton, the TON they
     * carry is the relayer's to attach; selling TON, it is the sale, which the account holds.
     */
    private suspend fun buildTonMessages(
        wallet: WalletEntity,
        account: AccountWithDetails,
        energy: AccountWithDetails,
        messages: List<PayloadMeta.Ton.Main>,
        sold: BigInteger,
        estimated: Fee?,
        purpose: GaslessRelayer.Purpose,
    ): TxFeeState {
        val attachedEnergy = if (account.asset.value is Asset.Token) {
            messages.fold(BigInteger.ZERO) { sum, message -> sum + message.coinValue() }
        } else {
            BigInteger.ZERO
        }
        val native = TxFeeState(energy = energy, estimated = estimated, attachedEnergy = attachedEnergy)

        // A jetton deposit's transfer is chainkit's to make (tonkeeper/chain-kit#89): its answer is not
        // signed here, so no relayer is offered for it.
        if (messages.any { it.token != null }) {
            return native
        }

        // Every part is held to the sale as a whole.
        val calls = messages.map { message -> GaslessRelayer.tonSwap(account, message, sold, purpose) ?: return native }
        return buildRelayed(
            wallet = wallet,
            account = account,
            energy = energy,
            call = calls.first(),
            amount = sold,
            estimated = estimated,
            relayedCalls = calls,
            // The relayer prices one payload at a time: the parts of a route do not depend on one another.
            estimatedCalls = calls,
            attachedEnergy = attachedEnergy,
        )
    }

    /**
     * An EVM swap is its call, with the approve it spends the allowance of ahead of it. The relayer's
     * estimate prices a single call, and a swap alone reverts without its approve: the two go to the
     * relayer as one batch, or the route stays with the wallet paying.
     */
    private suspend fun buildEvmSwap(
        wallet: WalletEntity,
        account: AccountWithDetails,
        energy: AccountWithDetails,
        payloads: List<Payload>,
        sale: Sale,
        estimated: Fee?,
        purpose: GaslessRelayer.Purpose,
    ): TxFeeState {
        val native = TxFeeState(energy = energy, estimated = estimated)

        val calls = payloads.map { payload ->
            val meta = payload.meta as? PayloadMeta.Evm.Main ?: return native
            GaslessRelayer.evmMain(account, meta, purpose) ?: return native
        }
        val swap = calls.last()
        val batch = if (calls.size > 1) {
            relayedBatch(account, calls) ?: return native
        } else {
            null
        }

        // The coin sold goes along with the call as its value; the tokens sold are inside the calldata,
        // and only what the swap is told to sell counts them.
        val spent = if (account.asset.value is Asset.Coin) {
            maxOf(swap.value, sale.fitted)
        } else {
            sale.fitted
        }

        return buildRelayed(
            wallet = wallet,
            account = account,
            energy = energy,
            call = batch?.whole ?: swap,
            amount = spent,
            nativeAmount = sale.native(spent),
            estimated = estimated,
            relayedCalls = batch?.calls,
        )
    }

    /**
     * A Solana swap is a deposit the relayer makes the transfer of, memo and all, or the provider's own
     * transaction, which names no amount: what the swap is told to sell stands for what it takes.
     */
    private suspend fun buildSolanaSwap(
        wallet: WalletEntity,
        account: AccountWithDetails,
        energy: AccountWithDetails,
        meta: PayloadMeta.Solana,
        sale: Sale,
        estimated: Fee?,
        purpose: GaslessRelayer.Purpose,
    ): TxFeeState {
        val call = GaslessRelayer.solanaSwap(account, meta, sale.sold, purpose)
            ?: return TxFeeState(energy = energy, estimated = estimated)

        val deposit = meta as? PayloadMeta.Solana.Main
        val spent = when {
            account.asset.value is Asset.Coin -> maxOf(deposit?.value ?: BigInteger.ZERO, sale.fitted)
            deposit != null -> deposit.value
            else -> sale.fitted
        }

        return buildRelayed(
            wallet = wallet,
            account = account,
            energy = energy,
            call = call,
            amount = spent,
            nativeAmount = sale.native(spent),
            estimated = estimated,
        )
    }

    /**
     * A TRON payload goes to the relayer as the swap states it: a route's own transaction, or a `main` (a
     * call with its calldata, or TRX, with its memo) the relayer builds the transaction of. Whatever the
     * relayer refuses is paid in TRX alone.
     */
    private suspend fun buildTronCall(
        wallet: WalletEntity,
        account: AccountWithDetails,
        energy: AccountWithDetails,
        meta: PayloadMeta.Tron,
        sale: Sale,
        estimated: Fee?,
        purpose: GaslessRelayer.Purpose,
    ): TxFeeState {
        val call = tryOrNull { tronCall(account, meta, purpose) }
            ?: return TxFeeState(energy = energy, estimated = estimated)

        return buildRelayed(
            wallet = wallet,
            account = account,
            energy = energy,
            call = call,
            amount = sale.sold,
            nativeAmount = sale.native(sale.sold),
            estimated = estimated,
            // The transaction is signed as it was built: a fee in a token would be another one beside it.
            batteryOnly = true,
        )
    }

    private fun tronCall(account: AccountWithDetails, meta: PayloadMeta.Tron, purpose: GaslessRelayer.Purpose): GaslessRelayer.Call? {
        return when (meta) {
            is PayloadMeta.Tron.Prebuild -> GaslessRelayer.tronCall(account, meta.data, purpose)

            is PayloadMeta.Tron.Main -> GaslessRelayer.tronMain(account, meta, purpose)

            is PayloadMeta.Tron.PlainMessage -> null
        }
    }

    suspend fun buildOperation(
        account: AccountWithDetails,
        energy: AccountWithDetails,
        walletId: String,
        payload: Payload,
        spentAmount: BigInteger,
        estimated: Fee?,
    ): TxFeeState = withContext(Async.Io) {
        val native = TxFeeState(energy = energy, estimated = estimated)
        val chain = account.asset.value.chain

        if (batteryDisabled() || chain.network.mode != Network.Mode.Mainnet || payload.type != Payload.Type.Main) {
            return@withContext native
        }

        val wallet = mcWallet(walletId) ?: return@withContext native
        if (api.getConfig(wallet.network).batterySendDisabled) {
            return@withContext native
        }

        when (val meta = payload.meta) {
            is PayloadMeta.Ton.Main -> {
                val carriesBalance = (meta.mode ?: 0) and (TonSendMode.CARRY_ALL_REMAINING_BALANCE.value or TonSendMode.CARRY_ALL_REMAINING_INCOMING_VALUE.value) != 0
                if (carriesBalance || meta.stateInit != null || (meta.body == null && !meta.comment.isNullOrBlank())) {
                    return@withContext native
                }
                buildTonSwap(
                    wallet = wallet,
                    energy = energy,
                    messages = listOf(
                        SwapMessagePayload(
                            to = meta.to.display,
                            amount = meta.value,
                            data = meta.body,
                            stateInit = null,
                            bounce = meta.bounce,
                        )
                    ),
                    estimated = estimated,
                    attachedEnergy = meta.value,
                )
            }

            is PayloadMeta.Evm.Main -> {
                val call = GaslessRelayer.call(account, meta.to.display, meta.value, meta.data)
                    ?: return@withContext native
                val relayedCalls = relayedCalls(account, meta) ?: return@withContext native
                buildRelayed(
                    wallet = wallet,
                    account = account,
                    energy = energy,
                    call = call,
                    relayedCalls = relayedCalls,
                    amount = spentAmount,
                    estimated = estimated,
                )
            }

            else -> native
        }
    }

    private fun relayedCalls(account: AccountWithDetails, meta: PayloadMeta.Evm.Main): List<GaslessRelayer.Call>? {
        val sender = account.data.displayAddress
        if (!HermesSelfCall.isSelfCall(sender, meta.to.display)) {
            return GaslessRelayer.call(account, meta.to.display, meta.value, meta.data)?.let(::listOf)
        }
        val calls = HermesSelfCall.unbatch(sender, meta.value, meta.data, account.asset.value.chain) ?: return null
        return calls.map { call ->
            GaslessRelayer.call(account, call.target.display, call.value, call.data) ?: return null
        }
    }

    private suspend fun buildTonSwap(
        wallet: WalletEntity,
        energy: AccountWithDetails,
        messages: List<SwapMessagePayload>,
        estimated: Fee?,
        attachedEnergy: BigInteger,
    ): TxFeeState {
        val native = TxFeeState(energy = energy, estimated = estimated, attachedEnergy = attachedEnergy)

        val batteryConfig = batteryRepository.getConfig(wallet.network)
        val excessesAddress = batteryConfig.excessesAddress ?: return native
        val payloads = messages.map { it.copy(excessesAddress = excessesAddress) }

        val emulated = tryOrNull {
            val boc = sender.swapMessage(wallet, payloads)
                .createSignedBody(EmptyPrivateKeyEd25519(), internalMessage = true)
            emulateBattery(wallet, boc)
        } ?: return native

        val extra = emulated.consequences.event.extra
        val chain = energy.asset.value.chain
        val charges = BatteryMapper.calculateChargesAmount(Coins.of(abs(extra)).value, batteryConfig.chargeCost)
        val reserve = emulated.excess
            ?.let { BatteryMapper.calculateChargesAmount(Coins.of(it).value, batteryConfig.chargeCost) }
            ?.takeIf { it in 1 until charges }
            ?.let { BigInteger.fromInt(it) }
        val batteryOption = (
            batteryOption(wallet, chain, charges, batteryConfig.chargeCost)?.copy(reserve = reserve)
                ?: unpricedBattery(wallet, chain, batteryConfig.chargeCost)
            )?.takeIfAllowedBy(emulated) ?: return native

        val options = orderSwapFeeOptions(
            battery = batteryOption,
            native = nativeOption(energy, estimated, attachedEnergy),
            preferred = settingsRepository.getPreferredFeeMethod(wallet.id),
        )

        return TxFeeState(
            energy = energy,
            estimated = estimated,
            attachedEnergy = attachedEnergy,
            options = options,
            relayer = RelayerSend { signer, option ->
                sender.sendSwap(
                    wallet = wallet,
                    signer = signer,
                    option = option,
                    payloads = payloads,
                )
                null
            },
        )
    }

    private suspend fun buildTronSwap(
        wallet: WalletEntity,
        account: AccountWithDetails,
        energy: AccountWithDetails,
        transaction: Transaction.Swap,
        estimated: Fee?,
    ): TxFeeState {
        val native = TxFeeState(energy = energy, estimated = estimated)
        val asset = account.asset.value

        if (!transaction.data.isNullOrBlank()) {
            return native
        }

        // The transfer the battery relays is made of the recipient and the amount alone: a memo is not in it.
        if (!transaction.memo.isNullOrBlank()) {
            return native
        }

        val nativeTransfer = asset is Asset.Coin
        if (!nativeTransfer && (asset as? Asset.Token)?.type != TokenType.Trc20) {
            return native
        }

        val transfer = TronTransfer(
            from = account.data.displayAddress,
            to = transaction.to.display,
            amount = transaction.amount.toJavaBigInteger(),
            // Unread on the native path: TRX has no contract, the transfer only carries the address.
            contractAddress = (asset as? Asset.Token)?.contract?.display ?: TokenEntity.TRX.address,
        )

        val resources = tryOrNull {
            if (nativeTransfer) {
                api.tron.estimateNativeTransferResources(
                    from = transfer.from,
                    to = transfer.to,
                    amountSun = transfer.amount.toLong(),
                )
            } else {
                api.tron.estimateTransferResources(transfer)
            }
        } ?: return native

        // Free bandwidth already covers the transfer, so there is no chain fee to sponsor — and the
        // battery estimate rejects an all-zero request outright.
        if (resources.energy == 0 && resources.bandwidth == 0) {
            return native
        }

        val charges = tryOrNull {
            api.tron.estimateBatteryCharges(
                transfer = transfer,
                resources = resources,
                auth = unifiedAccountRepository.getAuthBy(wallet.id),
            )
        } ?: return native

        val chain = energy.asset.value.chain
        val chargeCost = batteryRepository.getConfig(wallet.network).chargeCost
        val batteryOption = batteryOption(wallet, chain, charges.charges, chargeCost)
            ?: unpricedBattery(wallet, chain, chargeCost)
            ?: return native

        val options = orderSwapFeeOptions(
            battery = batteryOption,
            native = nativeOption(energy, estimated),
            preferred = settingsRepository.getPreferredFeeMethod(wallet.id),
        )

        return TxFeeState(
            energy = energy,
            estimated = estimated,
            options = options,
            relayer = RelayerSend { signer, option ->
                sender.sendTron(
                    wallet = wallet,
                    signer = signer,
                    option = option,
                    transfer = transfer,
                    resources = resources,
                    nativeTransfer = nativeTransfer,
                )
            },
        )
    }

    private suspend fun buildTronUsdt(
        wallet: WalletEntity,
        tronAddress: String,
        energy: AccountWithDetails,
        amount: BigInteger,
        to: String,
        estimated: Fee?,
    ): TxFeeState {
        val nativeOption = nativeOption(energy, estimated)
        val native = TxFeeState(
            energy = energy,
            estimated = estimated,
            options = listOfNotNull(nativeOption),
        )

        val transfer = TronTransfer(
            from = tronAddress,
            to = to,
            amount = amount.toJavaBigInteger(),
            contractAddress = TokenEntity.TRC20_USDT,
        )

        val estimation = tryOrNull {
            val resources = api.tron.estimateTransferResources(transfer)
            resources to api.tron.estimateBatteryCharges(
                transfer = transfer,
                resources = resources,
                auth = unifiedAccountRepository.getAuthBy(wallet.id),
            )
        } ?: return native

        val (resources, charges) = estimation
        val batteryOption = batteryOption(
            wallet = wallet,
            chain = energy.asset.value.chain,
            charges = charges.charges,
            chargeCost = batteryRepository.getConfig(TonNetwork.MAINNET).chargeCost,
        ) ?: return native
        if (!TxFeeLogic.isSufficient(batteryOption)) {
            return native.copy(shortBattery = batteryOption)
        }

        return TxFeeState(
            energy = energy,
            estimated = estimated,
            options = listOfNotNull(batteryOption, nativeOption),
            relayer = RelayerSend { signer, option ->
                sender.sendTron(
                    wallet = wallet,
                    signer = signer,
                    option = option,
                    transfer = transfer,
                    resources = resources,
                )
            },
        )
    }

    private suspend fun buildTonJetton(
        wallet: WalletEntity,
        master: String,
        account: AccountWithDetails,
        energy: AccountWithDetails,
        amount: BigInteger,
        to: String,
        comment: String?,
        estimated: Fee?,
        swap: Boolean = false,
    ): TxFeeState {
        // Without a relayed option there is nothing to choose: leave the options empty so the screen
        // renders the plain chainkit fee row it always did.
        val native = TxFeeState(energy = energy, estimated = estimated)

        if (api.getConfig(wallet.network).batterySendDisabled) {
            return native
        }

        val batteryConfig = batteryRepository.getConfig(wallet.network)
        val excessesAddress = batteryConfig.excessesAddress ?: return native
        val transferCost = batteryConfig.transferCost(master)

        val balance = jettonBalance(wallet, master) ?: return native
        // Minting-on-demand and custom-payload jettons need the payload plumbing the legacy send has;
        // until that is ported they simply keep the plain chainkit fee.
        if (balance.isRequestMinting || balance.customPayloadApiUri != null) {
            return native
        }

        val transfer = tryOrNull {
            sender.jettonTransfer(wallet, balance, amount, to, comment)
        } ?: return native

        val emulated = tryOrNull {
            val boc = transfer.signForEstimation(
                internalMessage = true,
                excessesAddress = excessesAddress,
                jettonTransferAmount = transferCost,
            )
            emulateBattery(wallet, boc)
        }

        val extra = emulated?.consequences?.event?.extra ?: 0L
        val battery = emulated?.let {
            val chain = energy.asset.value.chain
            val charges = BatteryMapper.calculateChargesAmount(Coins.of(abs(extra)).value, batteryConfig.chargeCost)
            val priced = batteryOption(wallet, chain, charges, batteryConfig.chargeCost)
            // A swap keeps an unpriced battery listed, as the other swap paths do; a transfer drops it.
            val listed = when {
                priced != null -> priced
                swap -> unpricedBattery(wallet, chain, batteryConfig.chargeCost)
                else -> null
            }
            listed?.takeIfAllowedBy(it)
        }
        // A transfer lists the battery only when the charges cover it; a swap lists it either way.
        val batteryOption = battery?.takeIf { swap || TxFeeLogic.isSufficient(it) }
        val shortBattery = battery?.takeIf { batteryOption == null }

        val gaslessOption = if (!swap) {
            gaslessOption(
                wallet = wallet,
                master = master,
                account = account,
                transfer = transfer,
                excessesAddress = excessesAddress,
                amount = amount,
                rechargeMethods = batteryConfig.rechargeMethods,
                transferCost = transferCost,
            )
        } else {
            null
        }

        if (batteryOption == null && gaslessOption == null) {
            return native.copy(shortBattery = shortBattery)
        }

        val options = if (swap && batteryOption != null) {
            orderSwapFeeOptions(
                battery = batteryOption,
                native = nativeOption(energy, estimated),
                preferred = settingsRepository.getPreferredFeeMethod(wallet.id),
            )
        } else {
            listOfNotNull(batteryOption, gaslessOption, nativeOption(energy, estimated))
        }

        return TxFeeState(
            energy = energy,
            estimated = estimated,
            options = options,
            shortBattery = shortBattery,
            relayer = RelayerSend { signer, option ->
                sender.sendJetton(
                    wallet = wallet,
                    signer = signer,
                    option = option,
                    balance = balance,
                    excessesAddress = excessesAddress,
                    extra = extra,
                    amount = amount,
                    to = to,
                    comment = comment,
                )
                null
            },
        )
    }

    private suspend fun buildRelayedSwap(
        wallet: WalletEntity,
        account: AccountWithDetails,
        energy: AccountWithDetails,
        transaction: Transaction.Swap,
        approvals: List<Transaction>,
        estimated: Fee?,
        shrinkToFit: Boolean,
        sold: BigInteger?,
        relayApprovals: Boolean,
    ): TxFeeState {
        // The relayer's estimate prices a single call, and a swap alone reverts without the approve
        // ahead of it: the two go to the relayer as one batch, or the route stays with the wallet paying.
        val swap = GaslessRelayer.swap(account, transaction)
        val batch = when {
            approvals.isEmpty() || swap == null -> null
            relayApprovals -> relayedBatch(account, approvals, swap)
                ?: return TxFeeState(energy = energy, estimated = estimated)
            else -> return TxFeeState(energy = energy, estimated = estimated)
        }

        // With calldata the amount is the coin sent along with the call; the tokens sold are inside it,
        // and only [sold] counts them. A prebuilt transaction carries no amount at all.
        val spent = when {
            account.asset.value is Asset.Coin -> maxOf(transaction.amount, sold ?: BigInteger.ZERO)
            transaction.data.isNullOrBlank() -> transaction.amount
            else -> sold ?: BigInteger.ZERO
        }

        return buildRelayed(
            wallet = wallet,
            account = account,
            energy = energy,
            call = batch?.whole ?: swap,
            amount = spent,
            nativeAmount = if (shrinkToFit) {
                BigInteger.ZERO
            } else {
                spent
            },
            estimated = estimated,
            relayedCalls = batch?.calls,
        )
    }

    // Calls the relayer carries together: priced as [whole], the one call of the sender's to itself
    // that runs them, and emulated and sent as [calls].
    private class RelayedBatch(
        val whole: GaslessRelayer.Call,
        val calls: List<GaslessRelayer.Call>,
    )

    /**
     * The [approvals] and the [swap] that spends what they allow, as one batch: the swap then finds its
     * allowance granted in the same transaction. Null when one of them is not a call the relayer carries.
     */
    private fun relayedBatch(
        account: AccountWithDetails,
        approvals: List<Transaction>,
        swap: GaslessRelayer.Call,
    ): RelayedBatch? {
        val chain = account.asset.value.chain
        if (!chain.isEvm) {
            return null
        }

        val approves = approvals.map { approval ->
            val call = approval as? Transaction.Call ?: return null
            GaslessRelayer.call(account, call.contract.display, call.amount, call.data) ?: return null
        }
        return relayedBatch(account, approves + swap)
    }

    /** [calls] in the order they run, as the one call of the sender's to itself that runs them all. */
    private fun relayedBatch(account: AccountWithDetails, calls: List<GaslessRelayer.Call>): RelayedBatch? {
        val chain = account.asset.value.chain
        if (!chain.isEvm) {
            return null
        }

        val data = HermesSelfCall.batch(
            calls.map { call -> Hermes.Call(Address.force(call.to, chain), call.value, call.data ?: EMPTY_CALLDATA) }
        )
        val whole = GaslessRelayer.call(account, account.data.displayAddress, BigInteger.ZERO, data) ?: return null
        return RelayedBatch(whole, calls)
    }

    /** [amount] is what [call] takes out of [account] in its own asset. */
    private suspend fun buildRelayed(
        wallet: WalletEntity,
        account: AccountWithDetails,
        energy: AccountWithDetails,
        call: GaslessRelayer.Call?,
        amount: BigInteger,
        estimated: Fee?,
        nativeAmount: BigInteger = amount,
        relayedCalls: List<GaslessRelayer.Call>? = null,
        // Only the battery is listed of what the relayer takes its fee in.
        batteryOnly: Boolean = false,
        // The coin the call carries, which a relayer attaches for the wallet.
        attachedEnergy: BigInteger = BigInteger.ZERO,
        // Calls priced one by one and summed, where the relayer cannot price them as one.
        estimatedCalls: List<GaslessRelayer.Call>? = null,
    ): TxFeeState {
        val native = TxFeeState(energy = energy, estimated = estimated, attachedEnergy = attachedEnergy)

        if (wallet.isMultichainLedger || api.getConfig(wallet.network).batterySendDisabled) {
            return native
        }
        call ?: return native
        val auth = unifiedAccountRepository.getAuthBy(wallet.id)
        if (auth.walletId == null) {
            return native
        }

        if (!relayerCarries(account.asset.value.chain)) {
            return native
        }
        val estimations = tryOrNull { estimateAll(gaslessRelayer, auth, estimatedCalls ?: listOf(call)) }
            ?.filter { !batteryOnly || it.assetId == Asset.Battery.ID }
            ?: return native

        val assetIds = mutableMapOf<String, String>()
        val relayed = estimations.mapNotNull { estimation ->
            relayedOption(wallet, account, energy, estimation)
                ?.let { it.copy(available = estimation.available && (it.isBattery || relayedCanPay(it, account, amount))) }
                ?.also { assetIds[it.id] = estimation.assetId }
        }
        if (relayed.isEmpty()) {
            return native
        }

        val nativeOption = estimated
            ?.let { TxFee(account = FeeAccount.Chain(energy), fee = it) }
            ?.let { option ->
                // Paid by the wallet, the coin the call carries comes out of the same balance as the fee.
                val carried = TxFeeLogic.requiredEnergy(option.fee, attachedEnergy) <= energy.unitBalance.value
                option.copy(available = carried && relayedCanPay(option, account, nativeAmount))
            }

        return TxFeeState(
            energy = energy,
            estimated = estimated,
            attachedEnergy = attachedEnergy,
            options = TxFeeLogic.prioritize(
                options = relayed + listOfNotNull(nativeOption),
                sentAssetId = account.asset.id,
                preferred = settingsRepository.getPreferredFeeMethod(wallet.id),
            ),
            quote = RelayerQuote { state, option ->
                quoteRelayed(
                    wallet = wallet,
                    account = account,
                    amount = amount,
                    calls = relayedCalls ?: listOf(call),
                    assetId = assetIds[option.id],
                    state = state,
                    option = option,
                )
            },
        )
    }

    private suspend fun relayedOption(
        wallet: WalletEntity,
        account: AccountWithDetails,
        energy: AccountWithDetails,
        estimation: GaslessRelayer.Estimation,
    ): TxFee? {
        if (estimation.assetId == Asset.Battery.ID) {
            return batteryOption(
                wallet = wallet,
                chain = energy.asset.value.chain,
                charges = estimation.fee.intValue(exactRequired = false),
                chargeCost = batteryRepository.getConfig(TonNetwork.MAINNET).chargeCost,
            )
        }

        val payerAssetId = walletAssetId(estimation.assetId, account.asset.value.chain)
        val payer = if (payerAssetId.equals(account.asset.id, ignoreCase = true)) {
            account
        } else {
            tryOrNull { accountRepo.findAccount(wallet.id, payerAssetId) }
        }
        val asset = payer?.asset?.value as? Asset.Token ?: return null
        return TxFee(
            account = FeeAccount.Chain(payer),
            fee = Fee.Value(asset, estimation.fee),
        )
    }

    private suspend fun quoteRelayed(
        wallet: WalletEntity,
        account: AccountWithDetails,
        amount: BigInteger,
        calls: List<GaslessRelayer.Call>,
        assetId: String?,
        state: TxFeeState,
        option: TxFee,
    ): TxFeeState? = withContext(Async.Io) {
        assetId ?: return@withContext null
        // A fee in a TON jetton goes out of the account's own wallet of it.
        val feeWallet = TON_JETTON.matchEntire(assetId)?.groupValues?.get(1)
            ?.let { master -> tryOrNull { jettonBalance(wallet, master) }?.walletAddress }
        val emulation = tryOrNull {
            gaslessRelayer.emulate(unifiedAccountRepository.getAuthBy(wallet.id), calls, assetId, feeWallet)
        } ?: return@withContext null

        val quoted = option.copy(fee = Fee.Value(option.fee.asset, emulation.fee))
        if (!quoted.isBattery && !relayedCanPay(quoted, account, amount)) {
            return@withContext null
        }

        state.copy(
            options = state.options.map { listed ->
                if (listed.id == quoted.id) {
                    quoted
                } else {
                    listed
                }
            },
            relayer = RelayerSend { signer, selected ->
                if (selected.id != quoted.id) {
                    throw IllegalStateException("${selected.id} was not emulated")
                }
                sender.sendOperation(
                    wallet = wallet,
                    account = account.data.chainAccount,
                    signer = signer,
                    option = selected,
                    emulation = emulation,
                    calls = calls,
                )
            },
            quotedId = quoted.id,
            relayedOperation = emulation.intended,
        )
    }

    /**
     * Fee paid in the transferred jetton itself: the relayer is repaid with an extra jetton gift in
     * the same message, so the wallet needs no TON at all. W5-only, and only for jettons the battery
     * service accepts as a recharge method.
     */
    private suspend fun gaslessOption(
        wallet: WalletEntity,
        master: String,
        account: AccountWithDetails,
        transfer: TransferEntity,
        excessesAddress: AddrStd,
        amount: BigInteger,
        rechargeMethods: List<RechargeMethodEntity>,
        transferCost: Coins,
    ): TxFee? {
        if (!wallet.isSupportedFeature(WalletFeature.GASLESS)) {
            return null
        }
        val supported = rechargeMethods.any { method ->
            method.supportGasless && method.jettonMaster?.equalsAddress(master) == true
        }
        if (!supported) {
            return null
        }

        val auth = unifiedAccountRepository.getAuthBy(wallet.id)
        if (auth.isEmpty) {
            return null
        }

        val decimals = transfer.token.decimals
        val commission = tryOrNull {
            val boc = transfer.signForEstimation(
                internalMessage = true,
                excessesAddress = excessesAddress,
                additionalGifts = listOf(
                    transfer.gaslessInternalGift(
                        jettonAmount = Coins.of(1, decimals),
                        batteryAddress = excessesAddress,
                    )
                ),
                jettonTransferAmount = transferCost,
            )
            api.estimateGaslessCost(
                auth = auth,
                jettonMaster = master,
                cell = boc,
                network = wallet.network,
            )
        } ?: return null

        val fee = runCatching { BigInteger.parseString(commission) }.getOrNull() ?: return null
        // The commission is charged in the same jetton that is being sent, so it has to fit next to
        // the transfer amount rather than just under the balance.
        if (fee + amount > account.unitBalance.value) {
            return null
        }

        return TxFee(
            account = FeeAccount.Chain(account),
            // Denominated in the jetton being sent, which is also what marks it as relayed.
            fee = Fee.Value(account.asset.value, fee),
        )
    }

    private fun orderSwapFeeOptions(
        battery: TxFee,
        native: TxFee?,
        preferred: PreferredFeeMethod,
    ): List<TxFee> {
        val batteryOk = TxFeeLogic.isSufficient(battery)
        val nativeOk = native != null && TxFeeLogic.isSufficient(native)
        if (batteryOk && !nativeOk) {
            return listOfNotNull(battery, native)
        }
        return when (preferred) {
            PreferredFeeMethod.TON -> listOfNotNull(native, battery)
            PreferredFeeMethod.BATTERY,
            PreferredFeeMethod.UNSPECIFIED,
            PreferredFeeMethod.GASLESS -> listOfNotNull(battery, native)
        }
    }

    private suspend fun batteryOption(
        wallet: WalletEntity,
        chain: Chain,
        charges: Int,
        chargeCost: String,
    ): TxFee? {
        if (charges <= 0) {
            return null
        }
        return TxFee(
            account = batteryAccount(wallet, chargeCost),
            // Battery is not on-chain: its charges are counted against the transaction's own chain.
            fee = Fee.Value(Asset.Battery(chain), BigInteger.fromInt(charges)),
        )
    }

    /**
     * The battery of a swap the relayer pays but did not price: listed, never payable, so the picker
     * still offers the recharge that gets it priced. Only for a wallet with no charges, which is what
     * such an estimate comes back for: with charges in hand, or unknown ones, a recharge is not what
     * is missing, and there is nothing to list.
     */
    private suspend fun unpricedBattery(wallet: WalletEntity, chain: Chain, chargeCost: String): TxFee? {
        val account = batteryAccount(wallet, chargeCost)
        val balance = account.balance ?: return null
        if (balance > BigInteger.ZERO) {
            return null
        }
        return TxFee(
            account = account,
            fee = Fee.Value(Asset.Battery(chain), BigInteger.ZERO),
        )
    }

    private suspend fun batteryAccount(wallet: WalletEntity, chargeCost: String) = FeeAccount.Keeper(
        balance = tryOrNull { batteryRepository.getCharges(wallet) }
            ?.let { BigInteger.fromInt(it) },
        rate = chargeRate(wallet, chargeCost),
    )

    /**
     * Prices a TON message against the battery whatever the charges: a battery that cannot pay now
     * still has to be listed for the picker and the shortage dialog to offer its recharge. Null when
     * the battery does not relay this kind of message.
     */
    private suspend fun emulateBattery(wallet: WalletEntity, boc: Cell): EmulateWithBatteryResult? {
        return batteryRepository.emulate(
            wallet = wallet,
            boc = boc,
            forceRelayer = true,
            safeModeEnabled = settingsRepository.isSafeModeEnabled(wallet.id, wallet.network),
        )?.takeIf { it.supportedByBattery }
    }

    // A battery the relayer refused is still listed when it is short on charges, which a recharge
    // fixes; one that has the charges yet is refused is out for the battery's own reasons.
    private fun TxFee.takeIfAllowedBy(emulated: EmulateWithBatteryResult): TxFee? {
        return takeIf { emulated.withBattery || !TxFeeLogic.isSufficient(it) }
    }

    // Charges are priced in TON, so one charge is worth chargeCost TON at the wallet's TON rate.
    private suspend fun chargeRate(wallet: WalletEntity, chargeCost: String): FiatRate? {
        val ton = tryOrNull {
            accountRepo.findAccount(wallet.id, tonCoinAssetId(testnet = false))
        }
        val tonRate = ton?.rate?.value ?: return null
        val cost = runCatching { BigDecimal.parseString(chargeCost) }.getOrNull() ?: return null
        return FiatRate(
            value = tonRate.value.multiply(cost),
            currency = tonRate.currency,
        )
    }

    private suspend fun jettonBalance(wallet: WalletEntity, master: String): BalanceEntity? =
        withContext(Async.Io) {
            tokenRepository.getJettonBalance(
                accountId = wallet.accountId,
                jettonMaster = master,
                network = wallet.network,
            )
        }

    private suspend fun relayerCarries(chain: Chain): Boolean {
        val chainId = GaslessRelayer.chainId(chain) ?: return false
        return chainId in batteryRepository.getGaslessChains(TonNetwork.MAINNET)
    }

    private fun batteryDisabled(): Boolean {
        return api.getConfig(TonNetwork.MAINNET).flags.disableBattery
    }

    private suspend fun mcWallet(walletId: String): WalletEntity? {
        return unifiedAccountRepository.getTonWalletById(walletId)
    }

    private suspend fun <T> tryOrNull(block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }
}

// chain/network/type/contract
private const val TOKEN_ASSET_ID_PARTS = 4

// `ton/mainnet/jetton/<master>`: a fee in a TON jetton.
private val TON_JETTON = Regex("^ton/mainnet/jetton/(.+)$")
private const val EMPTY_CALLDATA = "0x"

internal fun BigInteger.toJavaBigInteger(): java.math.BigInteger = java.math.BigInteger(toString())

/**
 * What a swap takes of the account's asset. The amounts are signed as prepared, so a fee in what is
 * sold has to fit next to [sold]. A sale that [shrinksToFit] is the exception: it is prepared again
 * to sell what such a fee leaves, so nothing is held against the fee.
 */
private class Sale(val sold: BigInteger, val shrinksToFit: Boolean) {

    val fitted: BigInteger
        get() = if (shrinksToFit) {
            BigInteger.ZERO
        } else {
            sold
        }

    /** What the chain's own fee is held against, where [spent] is what the swap takes. */
    fun native(spent: BigInteger): BigInteger = if (shrinksToFit) {
        BigInteger.ZERO
    } else {
        spent
    }
}

private fun nativeOption(
    energy: AccountWithDetails,
    fee: Fee?,
    attachedEnergy: BigInteger = BigInteger.ZERO,
): TxFee? {
    fee ?: return null
    val option = TxFee(account = FeeAccount.Chain(energy), fee = fee)
    return option.takeIf { TxFeeLogic.requiredEnergy(fee, attachedEnergy) <= energy.unitBalance.value }
}

// The relayer may label a token with another type than the wallet does (BSC: erc20, the wallet
// bep20). A token is its contract on its chain, so its id is rebuilt the wallet's way. Only on EVM:
// the rebuilt id is lowercased, and a Solana mint is case-sensitive base58.
private fun walletAssetId(relayerAssetId: String, chain: Chain): String {
    val parts = relayerAssetId.split('/')
    if (!chain.isEvm ||
        parts.size != TOKEN_ASSET_ID_PARTS ||
        parts[0] != chain.network.type.id ||
        parts[1] != chain.network.mode.id
    ) {
        return relayerAssetId
    }
    return chain.tokenAssetId(parts[3])
}

// A fee in the asset being sent has to fit next to the amount, not just under the balance.
private fun relayedCanPay(option: TxFee, account: AccountWithDetails, amount: BigInteger): Boolean {
    val balance = option.account.balance ?: return false
    val payer = (option.account as? FeeAccount.Chain)?.value
    val spent = if (payer != null && payer.asset.id.equals(account.asset.id, ignoreCase = true)) {
        amount
    } else {
        BigInteger.ZERO
    }
    return option.fee.amount + spent <= balance
}

/**
 * [calls] priced one by one: an asset pays for them together only where it pays for every one, at the
 * sum of what each costs.
 */
private fun estimateAll(
    relayer: GaslessRelayer,
    auth: Authorization,
    calls: List<GaslessRelayer.Call>,
): List<GaslessRelayer.Estimation> {
    val each = calls.map { call -> relayer.estimate(auth, call) }
    if (each.size == 1) {
        return each.single()
    }

    return each.first().mapNotNull { estimation ->
        val parts = each.map { estimations -> estimations.firstOrNull { it.assetId == estimation.assetId } ?: return@mapNotNull null }
        GaslessRelayer.Estimation(
            assetId = estimation.assetId,
            fee = parts.fold(BigInteger.ZERO) { sum, part -> sum + part.fee },
            available = parts.all { it.available },
        )
    }
}
