package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.WalletFeature
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.tron.TronTransfer
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.account.TokenType
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.core.components.tokenAssetId
import com.tonapps.icu.Coins
import com.tonapps.log.L
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.EmulateWithBatteryResult
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.account.tonCoinAssetId
import com.tonapps.wallet.data.settings.BatteryTransaction
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
    private val evmGasless: EvmGasless,
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

        if (asset.chain.isEvm) {
            if (!settingsRepository.batteryIsEnabledTx(wallet.accountId, BatteryTransaction.JETTON)) {
                return@withContext native
            }
            return@withContext buildEvm(
                wallet = wallet,
                account = account,
                energy = energy,
                call = EvmGasless.transfer(account, to, amount),
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

            TokenType.Jetton -> buildTonJetton(
                wallet = wallet,
                master = asset.contract.display,
                account = account,
                energy = energy,
                amount = amount,
                to = to,
                comment = comment,
                estimated = estimated,
            )

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
    ): TxFeeState = withContext(Async.Io) {
        val native = TxFeeState(energy = energy, estimated = estimated, attachedEnergy = attachedEnergy)
        val transaction = transactions.first()
        val asset = account.asset.value
        val chain = asset.chain

        if (batteryDisabled() || (!chain.isTvm && chain !is Chain.Tron && !chain.isEvm)) {
            return@withContext native
        }

        if (chain.network.mode != Network.Mode.Mainnet) {
            return@withContext native
        }

        val wallet = mcWallet(walletId) ?: return@withContext native
        if (api.getConfig(wallet.network).batterySendDisabled) {
            return@withContext native
        }

        if (!settingsRepository.batteryIsEnabledTx(wallet.accountId, BatteryTransaction.SWAP)) {
            return@withContext native
        }

        when {
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

            chain.isTvm -> buildTonSwap(wallet, energy, transactions, estimated, attachedEnergy)
            chain is Chain.Tron -> buildTronSwap(wallet, account, energy, transaction, estimated)
            chain.isEvm -> buildEvmSwap(wallet, account, energy, transaction, approvals, estimated, shrinkToFit)
            else -> native
        }
    }

    private suspend fun buildTonSwap(
        wallet: WalletEntity,
        energy: AccountWithDetails,
        transactions: List<Transaction.Swap>,
        estimated: Fee?,
        attachedEnergy: BigInteger,
    ): TxFeeState {
        val native = TxFeeState(energy = energy, estimated = estimated, attachedEnergy = attachedEnergy)

        val batteryConfig = batteryRepository.getConfig(wallet.network)
        val excessesAddress = batteryConfig.excessesAddress ?: return native
        val payloads = transactions.map { transaction ->
            SwapMessagePayload(
                to = transaction.to.display,
                amount = transaction.amount,
                data = transaction.data,
                stateInit = transaction.initData,
                excessesAddress = excessesAddress,
            )
        }

        val emulated = tryOrNull {
            val boc = sender.swapMessage(wallet, payloads)
                .createSignedBody(EmptyPrivateKeyEd25519(), internalMessage = true)
            emulateBattery(wallet, boc)
        } ?: return native

        val extra = emulated.consequences.event.extra
        val chain = energy.asset.value.chain
        val charges = BatteryMapper.calculateChargesAmount(Coins.of(abs(extra)).value, batteryConfig.chargeCost)
        val batteryOption = (
            batteryOption(wallet, chain, charges, batteryConfig.chargeCost)
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

        val balance = jettonBalance(wallet, master) ?: return native
        // Minting-on-demand and custom-payload jettons need the payload plumbing the legacy send has;
        // until that is ported they simply keep the plain chainkit fee.
        if (balance.isRequestMinting || balance.customPayloadApiUri != null) {
            return native
        }

        val transfer = tryOrNull {
            sender.jettonTransfer(wallet, balance, amount, to, comment)
        } ?: return native

        val batteryTx = if (swap) {
            BatteryTransaction.SWAP
        } else {
            BatteryTransaction.JETTON
        }
        val emulated = if (settingsRepository.batteryIsEnabledTx(wallet.accountId, batteryTx)) {
            tryOrNull {
                val boc = transfer.signForEstimation(
                    internalMessage = true,
                    excessesAddress = excessesAddress,
                    jettonTransferAmount = TransferEntity.BASE_FORWARD_AMOUNT,
                )
                emulateBattery(wallet, boc)
            }
        } else {
            null
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
            },
        )
    }

    private suspend fun buildEvmSwap(
        wallet: WalletEntity,
        account: AccountWithDetails,
        energy: AccountWithDetails,
        transaction: Transaction.Swap,
        approvals: List<Transaction>,
        estimated: Fee?,
        shrinkToFit: Boolean,
    ): TxFeeState {
        // The relayer's estimate prices a single call, and a swap alone reverts without the approve
        // sent ahead of it: such a route stays with the wallet paying.
        if (approvals.isNotEmpty()) {
            return TxFeeState(energy = energy, estimated = estimated)
        }
        // With calldata the amount is the coin sent along with the call; the tokens sold are inside it.
        val spent = if (account.asset.value is Asset.Coin || transaction.data.isNullOrBlank()) {
            transaction.amount
        } else {
            BigInteger.ZERO
        }
        return buildEvm(
            wallet = wallet,
            account = account,
            energy = energy,
            call = EvmGasless.swap(account, transaction),
            amount = spent,
            nativeAmount = if (shrinkToFit) {
                BigInteger.ZERO
            } else {
                spent
            },
            estimated = estimated,
        )
    }

    /** [amount] is what [call] takes out of [account] in its own asset. */
    private suspend fun buildEvm(
        wallet: WalletEntity,
        account: AccountWithDetails,
        energy: AccountWithDetails,
        call: EvmGasless.Call?,
        amount: BigInteger,
        estimated: Fee?,
        nativeAmount: BigInteger = amount,
    ): TxFeeState {
        val native = TxFeeState(energy = energy, estimated = estimated)

        if (api.getConfig(wallet.network).batterySendDisabled) {
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
        val estimations = tryOrNull { evmGasless.estimate(auth, call) } ?: return native

        val assetIds = mutableMapOf<String, String>()
        val relayed = estimations.mapNotNull { estimation ->
            evmOption(wallet, account, energy, estimation)
                ?.let { it.copy(available = estimation.available && (it.isBattery || evmCanPay(it, account, amount))) }
                ?.also { assetIds[it.id] = estimation.assetId }
        }
        if (relayed.isEmpty()) {
            return native
        }

        val nativeOption = estimated
            ?.let { TxFee(account = FeeAccount.Chain(energy), fee = it) }
            ?.let { it.copy(available = evmCanPay(it, account, nativeAmount)) }

        return TxFeeState(
            energy = energy,
            estimated = estimated,
            options = TxFeeLogic.prioritize(
                options = relayed + listOfNotNull(nativeOption),
                sentAssetId = account.asset.id,
                preferred = settingsRepository.getPreferredFeeMethod(wallet.id),
            ),
            quote = RelayerQuote { state, option ->
                quoteEvm(
                    wallet = wallet,
                    account = account,
                    amount = amount,
                    call = call,
                    assetId = assetIds[option.id],
                    state = state,
                    option = option,
                )
            },
        )
    }

    private suspend fun evmOption(
        wallet: WalletEntity,
        account: AccountWithDetails,
        energy: AccountWithDetails,
        estimation: EvmGasless.Estimation,
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

    // The relayer may label a token with another type than the wallet does (BSC: erc20, the wallet
    // bep20). A token is its contract on its chain, so its id is rebuilt the wallet's way.
    private fun walletAssetId(relayerAssetId: String, chain: Chain): String {
        val parts = relayerAssetId.split('/')
        if (parts.size != TOKEN_ASSET_ID_PARTS ||
            parts[0] != chain.network.type.id ||
            parts[1] != chain.network.mode.id
        ) {
            return relayerAssetId
        }
        return chain.tokenAssetId(parts[3])
    }

    // A fee in the asset being sent has to fit next to the amount, not just under the balance.
    private fun evmCanPay(option: TxFee, account: AccountWithDetails, amount: BigInteger): Boolean {
        val balance = option.account.balance ?: return false
        val payer = (option.account as? FeeAccount.Chain)?.value
        val spent = if (payer != null && payer.asset.id.equals(account.asset.id, ignoreCase = true)) {
            amount
        } else {
            BigInteger.ZERO
        }
        return option.fee.amount + spent <= balance
    }

    private suspend fun quoteEvm(
        wallet: WalletEntity,
        account: AccountWithDetails,
        amount: BigInteger,
        call: EvmGasless.Call,
        assetId: String?,
        state: TxFeeState,
        option: TxFee,
    ): TxFeeState? = withContext(Async.Io) {
        assetId ?: return@withContext null
        val emulation = tryOrNull {
            evmGasless.emulate(unifiedAccountRepository.getAuthBy(wallet.id), call, assetId)
        } ?: return@withContext null

        val quoted = option.copy(fee = Fee.Value(option.fee.asset, emulation.fee))
        if (!quoted.isBattery && !evmCanPay(quoted, account, amount)) {
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
                sender.sendEvm(
                    wallet = wallet,
                    account = account.data.chainAccount,
                    signer = signer,
                    option = selected,
                    emulation = emulation,
                )
            },
            quotedId = quoted.id,
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
                jettonTransferAmount = TransferEntity.BASE_FORWARD_AMOUNT,
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

    private fun nativeOption(
        energy: AccountWithDetails,
        fee: Fee?,
        attachedEnergy: BigInteger = BigInteger.ZERO,
    ): TxFee? {
        fee ?: return null
        val option = TxFee(account = FeeAccount.Chain(energy), fee = fee)
        return option.takeIf { TxFeeLogic.requiredEnergy(fee, attachedEnergy) <= energy.unitBalance.value }
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
        val chainId = EvmGasless.chainId(chain) ?: return false
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

internal fun BigInteger.toJavaBigInteger(): java.math.BigInteger = java.math.BigInteger(toString())