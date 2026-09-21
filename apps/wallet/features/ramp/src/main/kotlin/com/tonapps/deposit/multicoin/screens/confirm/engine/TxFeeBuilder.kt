package com.tonapps.deposit.multicoin.screens.confirm.engine

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
import com.tonapps.icu.Coins
import com.tonapps.log.L
import com.tonapps.wallet.api.API
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
) {

    suspend fun build(
        account: AccountWithDetails,
        energy: AccountWithDetails,
        walletId: String,
        amount: BigInteger,
        to: String,
        comment: String?,
        estimated: Fee?,
    ): TxFeeState = withContext(Async.Io) {
        val native = TxFeeState(energy = energy, estimated = estimated)
        val asset = account.asset.value

        if (batteryDisabled() || asset !is Asset.Token) {
            return@withContext native
        }
        if (asset.chain.network.mode != Network.Mode.Mainnet) {
            return@withContext native
        }
        val wallet = mcWallet(walletId) ?: return@withContext native

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
        transaction: Transaction.Swap,
        estimated: Fee?,
    ): TxFeeState = withContext(Async.Io) {
        val native = TxFeeState(energy = energy, estimated = estimated)
        val asset = account.asset.value
        val chain = when (asset) {
            is Asset.Coin -> asset.chain
            is Asset.Token -> asset.chain
        }

        if (batteryDisabled() || (!chain.isTvm && chain !is Chain.Tron)) {
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
            chain.isTvm && asset is Asset.Token && transaction.data.isNullOrBlank() -> buildTonJetton(
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

            chain.isTvm -> buildTonSwap(wallet, energy, transaction, estimated)
            chain is Chain.Tron -> buildTronSwap(wallet, account, energy, transaction, estimated)
            else -> native
        }
    }

    private suspend fun buildTonSwap(
        wallet: WalletEntity,
        energy: AccountWithDetails,
        transaction: Transaction.Swap,
        estimated: Fee?,
    ): TxFeeState {
        val native = TxFeeState(energy = energy, estimated = estimated)

        val batteryConfig = batteryRepository.getConfig(wallet.network)
        val excessesAddress = batteryConfig.excessesAddress ?: return native
        val payload = SwapMessagePayload(
            to = transaction.to.display,
            amount = transaction.amount,
            data = transaction.data,
            stateInit = transaction.initData,
            excessesAddress = excessesAddress,
        )

        val emulated = tryOrNull {
            val boc = sender.swapMessage(wallet, payload)
                .createSignedBody(EmptyPrivateKeyEd25519(), internalMessage = true)
            batteryRepository.emulate(
                wallet = wallet,
                boc = boc,
                safeModeEnabled = settingsRepository.isSafeModeEnabled(wallet.id, wallet.network),
            )?.takeIf { it.withBattery }
        } ?: return native

        val extra = emulated.consequences.event.extra
        val batteryOption = batteryOption(
            wallet = wallet,
            charges = BatteryMapper.calculateChargesAmount(
                Coins.of(abs(extra)).value,
                batteryConfig.chargeCost,
            ),
            chargeCost = batteryConfig.chargeCost,
            requireSufficient = false,
        ) ?: return native

        val options = orderSwapFeeOptions(
            battery = batteryOption,
            native = nativeOption(energy, estimated),
            preferred = settingsRepository.getPreferredFeeMethod(wallet.id),
        )

        return TxFeeState(
            energy = energy,
            estimated = estimated,
            options = options,
            relayer = RelayerSend { cryptoWallet, option ->
                sender.sendSwap(
                    wallet = wallet,
                    cryptoWallet = cryptoWallet,
                    option = option,
                    payload = payload,
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

        val batteryOption = batteryOption(
            wallet = wallet,
            charges = charges.charges,
            chargeCost = batteryRepository.getConfig(wallet.network).chargeCost,
            requireSufficient = false,
        ) ?: return native

        val options = orderSwapFeeOptions(
            battery = batteryOption,
            native = nativeOption(energy, estimated),
            preferred = settingsRepository.getPreferredFeeMethod(wallet.id),
        )

        return TxFeeState(
            energy = energy,
            estimated = estimated,
            options = options,
            relayer = RelayerSend { cryptoWallet, option ->
                sender.sendTron(
                    wallet = wallet,
                    cryptoWallet = cryptoWallet,
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
            charges = charges.charges,
            chargeCost = batteryRepository.getConfig(TonNetwork.MAINNET).chargeCost,
        ) ?: return native

        return TxFeeState(
            energy = energy,
            estimated = estimated,
            options = listOfNotNull(batteryOption, nativeOption),
            relayer = RelayerSend { cryptoWallet, option ->
                sender.sendTron(
                    wallet = wallet,
                    cryptoWallet = cryptoWallet,
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
                batteryRepository.emulate(
                    wallet = wallet,
                    boc = boc,
                    safeModeEnabled = settingsRepository.isSafeModeEnabled(wallet.id, wallet.network),
                )?.takeIf { it.withBattery }
            }
        } else {
            null
        }

        val extra = emulated?.consequences?.event?.extra ?: 0L
        val batteryOption = emulated?.let {
            batteryOption(
                wallet = wallet,
                charges = BatteryMapper.calculateChargesAmount(
                    Coins.of(abs(extra)).value,
                    batteryConfig.chargeCost,
                ),
                chargeCost = batteryConfig.chargeCost,
                requireSufficient = !swap,
            )
        }

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
            return native
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
            relayer = RelayerSend { cryptoWallet, option ->
                sender.sendJetton(
                    wallet = wallet,
                    cryptoWallet = cryptoWallet,
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
            fee = Fee.Value(fee),
            viaRelayer = true,
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
        charges: Int,
        chargeCost: String,
        requireSufficient: Boolean = true,
    ): TxFee? {
        if (charges <= 0) {
            return null
        }
        val option = TxFee(
            account = FeeAccount.Keeper(
                balance = tryOrNull { batteryRepository.getCharges(wallet) }
                    ?.let { BigInteger.fromInt(it) },
                rate = chargeRate(wallet, chargeCost),
            ),
            fee = Fee.Value(BigInteger.fromInt(charges)),
            viaRelayer = true,
        )
        return if (requireSufficient) {
            option.takeIf { TxFeeLogic.isSufficient(it) }
        } else {
            option
        }
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

    private fun nativeOption(energy: AccountWithDetails, fee: Fee?): TxFee? {
        fee ?: return null
        val option = TxFee(account = FeeAccount.Chain(energy), fee = fee, viaRelayer = false)
        return option.takeIf { TxFeeLogic.isSufficient(it) }
    }

    private suspend fun jettonBalance(wallet: WalletEntity, master: String): BalanceEntity? =
        withContext(Async.Io) {
            tokenRepository.get(
                currency = settingsRepository.currency,
                accountId = wallet.accountId,
                network = wallet.network,
            )?.firstOrNull { it.address.equalsAddress(master) }?.balance
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

internal fun BigInteger.toJavaBigInteger(): java.math.BigInteger = java.math.BigInteger(toString())
