package com.tonapps.migration.data

import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.tron.TronTransaction
import com.tonapps.blockchain.tron.TronTransfer
import com.tonapps.bus.generated.Events.Migration.MigrationChain
import com.tonapps.core.flags.WalletFeature
import com.tonapps.icu.Coins
import com.tonapps.log.L
import com.tonapps.migration.analytics.MigrationAnalytics
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.tron.entity.TronResourcesEntity
import com.tonapps.wallet.api.withRetry
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.rates.entity.RatesEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import io.Serializer
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import io.tonapi.models.GetBlockchainRawAccountsRequest
import io.tonapi.models.MigrationPrepareRequest
import io.tonapi.models.MigrationPrepareResponse
import io.tonapi.models.MigrationWalletValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigInteger

private const val WALLETS_COUNT_CACHE_TTL_MS = 5 * 60_000L

class MigrationRepository(
    private val accountRepository: AccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
    private val batteryRepository: BatteryRepository,
    private val api: API,
    private val authorizationProvider: AuthorizationProvider,
) {

    @Volatile
    private var preparedMigration: MigrationPrepareResult? = null

    @Volatile
    private var walletsCountCache: WalletsCount? = null

    private val walletsCountMutex = Mutex()

    suspend fun loadWallets(): List<MigratableWallet>? = withContext(Async.Io) {
        val wallets = candidateWallets()
        if (wallets.isEmpty()) {
            return@withContext emptyList()
        }

        val currency = settingsRepository.currency
        val migrationByAccount = fetchMigrationValues(wallets, currency)
            ?: return@withContext null
        val rates = loadRates(currency)
        val tronBalancesByWalletId = loadTronBalances(wallets)

        wallets.mapNotNull { wallet ->
            MigratableWallet.create(
                wallet = wallet,
                value = migrationByAccount[wallet.accountId],
                tronBalances = tronBalancesByWalletId[wallet.id] ?: TronBalances.Empty,
                currency = currency,
                rates = rates,
            )
        }.sortedWith(
            compareByDescending<MigratableWallet> { it.fiatBalance }
                .thenByDescending { it.nftCount },
        )
    }

    // The cache key includes the candidate wallet ids, so adding or removing a TON wallet
    // invalidates the count without waiting out the TTL.
    suspend fun migratableWalletsCount(): Int {
        val candidateIds = candidateWallets().mapTo(mutableSetOf()) { it.id }
        walletsCountMutex.withLock {
            val cached = walletsCountCache
            if (cached != null && cached.candidateIds == candidateIds &&
                System.currentTimeMillis() - cached.loadedAt < WALLETS_COUNT_CACHE_TTL_MS
            ) {
                return cached.count
            }
            val count = loadWallets()?.size ?: return cached?.count ?: 0
            walletsCountCache = WalletsCount(
                count = count,
                candidateIds = candidateIds,
                loadedAt = System.currentTimeMillis(),
            )
            return count
        }
    }

    fun invalidateWalletsCount() {
        walletsCountCache = null
    }

    suspend fun prepare(wallet: MigratableWallet): MigrationPrepareResult? = withContext(Async.Io) {
        val source = wallet.wallet
        val mcWalletId = accountRepository.getSelectedWalletId()
        val destinationWallet = mcWalletId?.let { mcAccountRepository.getWallet(it) }
        if (mcWalletId == null || destinationWallet == null) {
            MigrationAnalytics.prepareError(chain = MigrationChain.Multichain, isBlocking = true)
            return@withContext null
        }

        val batteryCharges = availableBatteryCharges(source)

        val tonLeg = if (wallet.hasTonAssets) {
            val destinationAddress = mcAccountRepository.getTonAccount(mcWalletId)?.displayAddress
            if (destinationAddress == null) {
                MigrationAnalytics.prepareError(chain = MigrationChain.Multichain, isBlocking = true)
                return@withContext null
            }
            prepareTonLeg(
                source = source,
                from = source.accountId,
                to = destinationAddress,
                publicKey = if (!source.initialized) {
                    source.publicKey.hex()
                } else {
                    null
                },
                tonBalance = wallet.tonBalance,
                chargesBalance = batteryCharges,
            )
        } else {
            TonLegPrepare.Empty
        }

        var tronError: Throwable? = null
        val tronPrepare = try {
            prepareTron(
                source = source,
                destinationWallet = destinationWallet,
                balances = wallet.tronBalances,
                chargesBalance = batteryCharges,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e("MigrationRepository", "Failed to prepare TRON migration", e)
            tronError = e
            MigrationTronPrepare.Empty
        }

        val tonResponse = tonLeg.response
        val hasTonPrepare = !tonResponse?.transactions.isNullOrEmpty()
        if (wallet.hasTonAssets && tonResponse == null) {
            MigrationAnalytics.prepareError(
                chain = MigrationChain.Ton,
                isBlocking = !tronPrepare.hasTransfers,
            )
        }
        if (tronError != null) {
            MigrationAnalytics.prepareError(
                chain = MigrationChain.Tron,
                isBlocking = !hasTonPrepare,
                error = tronError,
            )
        }
        if (!hasTonPrepare && !tronPrepare.hasTransfers) {
            return@withContext null
        }

        val rates = loadRates(settingsRepository.currency)
        val availableTrx = wallet.tronBalances.migratableTrx
        val (preferredTonFee, preferredTron) = preferBatteryWhenBothCovered(
            tonFee = tonLeg.fee,
            tonOptions = tonLeg.feeOptions.copy(availableCharges = batteryCharges),
            tronPrepare = tronPrepare,
            availableCharges = batteryCharges,
            availableTrx = availableTrx,
            rates = rates,
        )
        val resolvedTron = resolveSharedBatterySelection(
            tonFee = preferredTonFee,
            tronPrepare = preferredTron,
            availableTrx = availableTrx,
            availableCharges = batteryCharges,
            rates = rates,
        )

        MigrationPrepareResult(
            wallet = wallet,
            response = tonResponse,
            destinationWallet = destinationWallet,
            tronPrepare = resolvedTron,
            tonFee = preferredTonFee,
            tonFeeOptions = tonLeg.feeOptions.copy(availableCharges = batteryCharges),
            requiredTonNano = tonLeg.insufficientDetails?.required,
            availableTonNano = tonLeg.insufficientDetails?.available,
        ).also { preparedMigration = it }
    }

    private fun preferBatteryWhenBothCovered(
        tonFee: MigrationTonFee,
        tonOptions: MigrationTonFeeOptions,
        tronPrepare: MigrationTronPrepare,
        availableCharges: Int,
        availableTrx: Coins,
        rates: RatesEntity,
    ): Pair<MigrationTonFee, MigrationTronPrepare> {
        val tonBattery = tonOptions.battery ?: return tonFee to tronPrepare
        val tronBattery = tronPrepare.feeOptions.battery ?: return tonFee to tronPrepare
        if (tonOptions.batteryTransactions.none { it.sponsored == true }) {
            return tonFee to tronPrepare
        }
        if (tonBattery.charges + tronBattery.charges > availableCharges) {
            return tonFee to tronPrepare
        }
        if (tonFee is MigrationTonFee.Battery && tronPrepare.fee is MigrationTronFee.Battery) {
            return tonFee to tronPrepare
        }
        return tonBattery to tronPrepare.withFee(
            fee = tronBattery,
            availableTrx = availableTrx,
            trxFiatOf = { amount -> rates.convert(TokenEntity.TRX.address, amount) },
        )
    }

    private fun resolveSharedBatterySelection(
        tonFee: MigrationTonFee,
        tronPrepare: MigrationTronPrepare,
        availableTrx: Coins,
        availableCharges: Int,
        rates: RatesEntity,
    ): MigrationTronPrepare {
        val tonCharges = (tonFee as? MigrationTonFee.Battery)?.charges ?: 0
        val tronBattery = tronPrepare.fee as? MigrationTronFee.Battery ?: return tronPrepare
        if (tonCharges + tronBattery.charges <= availableCharges) {
            return tronPrepare
        }
        val trx = tronPrepare.feeOptions.trx?.takeIf { tronPrepare.feeOptions.trxEnough }
            ?: return tronPrepare
        return tronPrepare.withFee(
            fee = trx,
            availableTrx = availableTrx,
            trxFiatOf = { amount -> rates.convert(TokenEntity.TRX.address, amount) },
        )
    }

    private data class TonLegPrepare(
        val response: MigrationPrepareResponse?,
        val fee: MigrationTonFee,
        val feeOptions: MigrationTonFeeOptions,
        val insufficientDetails: MigrationPrepareErrorDetails? = null,
    ) {
        companion object {
            val Empty = TonLegPrepare(
                response = null,
                fee = MigrationTonFee.None,
                feeOptions = MigrationTonFeeOptions.Empty,
            )
        }
    }

    private data class TonFeeCandidate(
        val response: MigrationPrepareResponse,
        val fee: MigrationTonFee,
        val enough: Boolean,
        val insufficientDetails: MigrationPrepareErrorDetails? = null,
    )

    private suspend fun prepareTonLeg(
        source: WalletEntity,
        from: String,
        to: String,
        publicKey: String?,
        tonBalance: Coins,
        chargesBalance: Int,
    ): TonLegPrepare = coroutineScope {
        val selfDeferred = async {
            prepareTonMigration(from, to, publicKey, MigrationPrepareRequest.GasPayer.self)
        }
        val batteryDeferred = async {
            if (!WalletFeature.MigrationBattery.isEnabled) {
                null
            } else {
                prepareTonMigration(from, to, publicKey, MigrationPrepareRequest.GasPayer.battery)
            }
        }

        val selfCandidate = selfDeferred.await()?.let { prepared ->
            val fee = MigrationTonFee.Ton(tonAmount = Coins.of(prepared.response.totalGasSpentNano()))
            TonFeeCandidate(
                response = prepared.response,
                fee = fee,
                enough = !prepared.insufficientFunds && tonBalance >= fee.tonAmount,
                insufficientDetails = prepared.insufficientDetails,
            )
        }
        val batteryCandidate = batteryDeferred.await()?.let { prepared ->
            batteryTonCandidate(
                source = source,
                prepared = prepared,
                chargesBalance = chargesBalance,
            )
        }

        val selected = when {
            batteryCandidate != null && batteryCandidate.enough -> batteryCandidate
            selfCandidate != null && selfCandidate.enough -> selfCandidate
            else -> selfCandidate ?: batteryCandidate
        }

        TonLegPrepare(
            response = selected?.response,
            fee = selected?.fee ?: MigrationTonFee.None,
            insufficientDetails = selected?.insufficientDetails,
            feeOptions = MigrationTonFeeOptions(
                ton = selfCandidate?.fee as? MigrationTonFee.Ton,
                tonEnough = selfCandidate?.enough == true,
                tonTransactions = selfCandidate?.response?.transactions.orEmpty(),
                battery = batteryCandidate?.fee as? MigrationTonFee.Battery,
                batteryEnough = batteryCandidate?.enough == true,
                batteryTransactions = batteryCandidate?.response?.transactions.orEmpty(),
                availableCharges = chargesBalance,
            ),
        )
    }

    private suspend fun batteryTonCandidate(
        source: WalletEntity,
        prepared: TonPrepared,
        chargesBalance: Int,
    ): TonFeeCandidate? {
        val sponsored = prepared.response.transactions.filter { it.sponsored == true }
        if (sponsored.isEmpty()) {
            return null
        }
        val sponsoredGas = sponsored.batteryChargeBasisNano()
        if (sponsoredGas <= 0L) {
            return null
        }

        return runCatching {
            val config = batteryRepository.getConfig(source.network)
            val tonAmount = Coins.of(sponsoredGas)
            val charges = BatteryMapper.convertToCharges(tonAmount, config.chargeCost)
            if (charges <= 0) {
                return@runCatching null
            }
            TonFeeCandidate(
                response = prepared.response,
                fee = MigrationTonFee.Battery(charges = charges, tonAmount = tonAmount),
                enough = !prepared.insufficientFunds && chargesBalance >= charges,
            )
        }.getOrNull()
    }

    private data class TonPrepared(
        val response: MigrationPrepareResponse,
        val insufficientFunds: Boolean,
        val insufficientDetails: MigrationPrepareErrorDetails? = null,
    )

    /**
     * 409 returns a normal prepare payload (transactions + fees) with an error envelope.
     * Recover the body so confirm can show the usual insufficient-funds dialog.
     */
    private fun prepareTonMigration(
        from: String,
        to: String,
        publicKey: String?,
        gasPayer: MigrationPrepareRequest.GasPayer,
    ): TonPrepared? {
        return try {
            val response = api.migration(TonNetwork.MAINNET).prepareMigration(
                MigrationPrepareRequest(
                    from = from,
                    to = to,
                    currency = settingsRepository.currency.code,
                    publicKey = publicKey,
                    gasPayer = gasPayer,
                ),
            )
            TonPrepared(response = response, insufficientFunds = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClientException) {
            if (e.statusCode == 409) {
                e.prepareResponseBody()?.let {
                    return it
                }
            }
            L.e("MigrationRepository", "Failed to prepare TON migration ($gasPayer)", e)
            null
        } catch (e: Throwable) {
            L.e("MigrationRepository", "Failed to prepare TON migration ($gasPayer)", e)
            null
        }
    }

    private fun ClientException.prepareResponseBody(): TonPrepared? {
        val body = (response as? ClientError<*>)?.body as? String ?: return null
        val prepareResponse = runCatching {
            Serializer.fromJSON<MigrationPrepareResponse>(body)
        }.getOrNull() ?: return null

        val errorBody = runCatching {
            Serializer.fromJSON<MigrationPrepareErrorBody>(body)
        }.getOrNull()
        val details = errorBody?.details?.takeIf {
            errorBody.errorCode == INSUFFICIENT_TON_GAS_CODE && it.required > 0
        }
        return TonPrepared(
            response = prepareResponse,
            insufficientFunds = true,
            insufficientDetails = details,
        )
    }

    private suspend fun availableBatteryCharges(source: WalletEntity): Int {
        if (!WalletFeature.MigrationBattery.isEnabled) {
            return 0
        }
        if (api.getConfig(source.network).flags.disableBattery) {
            return 0
        }
        return runCatching {
            batteryRepository.getCharges(wallet = source, ignoreCache = true)
        }.getOrDefault(0)
    }

    fun prepared(walletId: String): MigrationPrepareResult? {
        return preparedMigration?.takeIf { it.wallet.wallet.id == walletId }
    }

    suspend fun completeRaffleMigration(mcWalletId: String) = withContext(Async.Io) {
        try {
            api.multichain.raffles.completeWalletRaffleMigration(mcWalletId, xWalletId = mcWalletId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e("MigrationRepository", "Failed to report raffle migration", e)
        }
    }

    private suspend fun prepareTron(
        source: WalletEntity,
        destinationWallet: McWalletEntity,
        balances: TronBalances,
        chargesBalance: Int,
    ): MigrationTronPrepare {
        if (!balances.hasBalance || api.getConfig(TonNetwork.MAINNET).flags.disableTron) {
            return MigrationTronPrepare.Empty
        }

        val fromAddress = accountRepository.getTronAddress(source.id) ?: return MigrationTronPrepare.Empty
        val toAddress = mcAccountRepository.getCoinAccounts(destinationWallet.id)
            .firstOrNull { it.network.equals("tron", ignoreCase = true) }
            ?.displayAddress
            ?: return MigrationTronPrepare.Empty
        if (fromAddress.equals(toAddress, ignoreCase = true)) {
            return MigrationTronPrepare.Empty
        }

        val currency = settingsRepository.currency
        val rates = loadRates(currency)
        val usdtAmount = balances.usdt
        val trxBalance = balances.trx
        val migratableTrx = balances.migratableTrx

        val usdtBlock = if (usdtAmount.isPositive) {
            val transfer = TronTransfer(
                from = fromAddress,
                to = toAddress,
                amount = usdtAmount.toBigInteger(),
                contractAddress = TokenEntity.TRC20_USDT,
            )
            val resources = api.tron.estimateTransferResources(transfer)
            val selection = selectUsdtFee(
                source = source,
                transfer = transfer,
                resources = resources,
                trxBalance = trxBalance,
                rates = rates,
                chargesBalance = chargesBalance,
            )
            UsdtPrepareBlock(
                transfer = transfer,
                resources = resources,
                fee = selection.fee,
                feeOptions = selection.options,
                usdtRequiredTrx = selection.options.trx?.amount ?: Coins.ZERO,
            )
        } else {
            null
        }

        val sendUsdtAmount = usdtAmount.takeIf { usdtBlock != null } ?: Coins.ZERO

        val bandwidthAfterUsdt = usdtBlock?.resources?.let { usdtResources ->
            (api.tron.getAccountFreeBandwidth(fromAddress) - usdtResources.bandwidth)
                .coerceAtLeast(0)
        }
        val provisionalAmountSun = migratableTrx.toBigInteger().max(BigInteger.ONE).toLong()
        val trxResources = if (migratableTrx.isPositive) {
            api.tron.estimateNativeTransferResources(
                from = fromAddress,
                to = toAddress,
                amountSun = provisionalAmountSun,
                bandwidthAvailableOverride = bandwidthAfterUsdt,
            )
        } else {
            null
        }
        val nativeRequiredTrx = if (trxResources != null) {
            bufferedNativeTrxFee(api.tron.estimateTrxFee(trxResources).fee)
        } else {
            Coins.ZERO
        }

        val nativeFeeSelection = if (sendUsdtAmount.isPositive || trxResources == null) {
            null
        } else {
            selectNativeTrxFee(
                migratableTrx = migratableTrx,
                nativeRequiredTrx = nativeRequiredTrx,
                rates = rates,
            )
        }

        val provisional = MigrationTronPrepare(
            fromAddress = fromAddress,
            toAddress = toAddress,
            usdtAmount = sendUsdtAmount,
            usdtTransfer = usdtBlock?.transfer,
            usdtResources = usdtBlock?.resources,
            trxResources = trxResources,
            fee = usdtBlock?.fee ?: nativeFeeSelection?.fee ?: MigrationTronFee.None,
            feeOptions = usdtBlock?.feeOptions ?: nativeFeeSelection?.options ?: MigrationUsdtFeeOptions.Empty,
            usdtRequiredTrx = usdtBlock?.usdtRequiredTrx ?: Coins.ZERO,
            nativeRequiredTrx = nativeRequiredTrx,
        )
        val trxAmount = provisional.trxTransferAmount(availableTrx = migratableTrx)
            .withoutMigrationDust()

        if (!sendUsdtAmount.isPositive && !trxAmount.isPositive) {
            val nativeShort = nativeFeeSelection?.options?.trx
                ?.takeIf { !nativeFeeSelection.options.trxEnough && migratableTrx.isPositive }
            if (nativeShort != null) {
                return provisional.copy(
                    trxAmount = Coins.ZERO,
                    trxFiat = Coins.ZERO,
                )
            }
            return MigrationTronPrepare.Empty
        }

        return provisional.copy(
            usdtFiat = if (sendUsdtAmount.isPositive) {
                rates.convert(TokenEntity.TRON_USDT.address, sendUsdtAmount)
            } else {
                Coins.ZERO
            },
            trxAmount = trxAmount,
            trxFiat = if (trxAmount.isPositive) {
                rates.convert(TokenEntity.TRX.address, trxAmount)
            } else {
                Coins.ZERO
            },
        )
    }

    suspend fun sendTronMigration(
        wallet: WalletEntity,
        prepare: MigrationTronPrepare,
        sign: suspend (TronTransaction) -> TronTransaction,
        onStepCompleted: suspend () -> Unit = {},
    ) = withContext(Async.Io) {
        if (!prepare.hasTransfers) {
            return@withContext
        }

        if (prepare.willSendUsdt) {
            sendUsdtMigration(
                wallet = wallet,
                prepare = prepare,
                sign = sign,
            )
            onStepCompleted()
        }

        val tryTrxSweep = prepare.willSweepTrx || prepare.willSendUsdt
        if (tryTrxSweep) {
            val swept = sendTrxSweep(
                prepare = prepare,
                sign = sign,
                allowSkipInsufficient = prepare.willSendUsdt && !prepare.willSweepTrx,
            )
            if (swept) {
                onStepCompleted()
            }
        }
    }

    private suspend fun sendUsdtMigration(
        wallet: WalletEntity,
        prepare: MigrationTronPrepare,
        sign: suspend (TronTransaction) -> TronTransaction,
    ) {
        val transfer = prepare.usdtTransfer
            ?: throw IllegalStateException("USDT transfer is missing")
        val resources = prepare.usdtResources
            ?: throw IllegalStateException("USDT resources are missing")

        val transaction = api.tron.buildSmartContractTransaction(transfer).extendExpiration()
        val signed = sign(transaction)

        when (prepare.fee) {
            is MigrationTronFee.Battery -> {
                val auth = authorizationProvider.getAuthBy(wallet.id)
                if (auth.isEmpty) {
                    throw IllegalStateException("Authorization is empty")
                }
                api.tron.sendWithBattery(
                    transaction = signed,
                    resources = resources,
                    tronAddress = prepare.fromAddress,
                    auth = auth,
                )
            }

            is MigrationTronFee.Trx,
            is MigrationTronFee.Ton,
            MigrationTronFee.None,
            -> {
                api.tron.sendWithTrx(
                    transaction = signed,
                    resources = resources,
                    tronAddress = prepare.fromAddress,
                )
            }
        }

        api.tron.waitForSuccessfulTransaction(signed.txId)
    }

    private suspend fun sendTrxSweep(
        prepare: MigrationTronPrepare,
        sign: suspend (TronTransaction) -> TronTransaction,
        allowSkipInsufficient: Boolean,
    ): Boolean {
        val amountSun = prepare.trxAmount
            .takeIf { it.isPositive }
            ?.toBigInteger()
            ?.toLong()
            ?: resolveTrxSweepAmountSun(
                from = prepare.fromAddress,
                to = prepare.toAddress,
            )
        if (amountSun == null) {
            if (allowSkipInsufficient) {
                return false
            }
            throw IllegalStateException("Unable to create TRX transfer")
        }

        return try {
            broadcastNativeTrx(
                prepare = prepare,
                amountSun = amountSun,
                sign = sign,
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (isInsufficientTrxBalance(e)) {
                val retried = retryNativeTrxWithLowerAmount(
                    prepare = prepare,
                    initialAmountSun = amountSun,
                    sign = sign,
                )
                if (retried) {
                    return true
                }
                if (allowSkipInsufficient) {
                    L.w(
                        "MigrationRepository",
                        "Skipping leftover TRX sweep after USDT: ${e.message}",
                    )
                    return false
                }
            }
            throw e
        }
    }

    private suspend fun retryNativeTrxWithLowerAmount(
        prepare: MigrationTronPrepare,
        initialAmountSun: Long,
        sign: suspend (TronTransaction) -> TronTransaction,
    ): Boolean {
        var amountSun = initialAmountSun
        repeat(4) {
            amountSun = (amountSun * 4L) / 5L
            if (amountSun <= 0L) {
                return false
            }
            try {
                broadcastNativeTrx(
                    prepare = prepare,
                    amountSun = amountSun,
                    sign = sign,
                )
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!isInsufficientTrxBalance(e)) {
                    throw e
                }
            }
        }
        return false
    }

    private suspend fun broadcastNativeTrx(
        prepare: MigrationTronPrepare,
        amountSun: Long,
        sign: suspend (TronTransaction) -> TronTransaction,
    ) {
        val unsigned = api.tron.buildNativeTransfer(
            from = prepare.fromAddress,
            to = prepare.toAddress,
            amountSun = amountSun,
        ).extendExpiration()
        val signed = sign(unsigned)
        api.tron.sendWithTrx(
            transaction = signed,
            resources = TronResourcesEntity(energy = 0, bandwidth = 0),
            tronAddress = prepare.fromAddress,
        )
        api.tron.waitForSuccessfulTransaction(signed.txId)
    }

    private fun isInsufficientTrxBalance(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("balance is not sufficient", ignoreCase = true)
    }

    private suspend fun resolveTrxSweepAmountSun(
        from: String,
        to: String,
    ): Long? {
        val balance = api.tron.getTrxBalance(from).value.withoutMigrationDust()
        val balanceSun = balance.toBigInteger().toLong()
        if (balanceSun <= 0L) {
            return null
        }

        val feeSun = api.tron.estimateTrxFee(
            api.tron.estimateNativeTransferResources(
                from = from,
                to = to,
                amountSun = balanceSun,
            )
        ).fee.toBigInteger().toLong()
        val reservedSun = bufferedNativeTrxFeeSun(feeSun)

        val amountSun = balanceSun - reservedSun
        if (amountSun <= 0L) {
            return null
        }

        val amount = Coins.of(amountSun, TokenEntity.TRX.decimals).withoutMigrationDust()
        return amount.toBigInteger().toLong().takeIf { it > 0L }
    }

    private fun bufferedNativeTrxFee(fee: Coins): Coins {
        return Coins.of(bufferedNativeTrxFeeSun(fee.toBigInteger().toLong()), TokenEntity.TRX.decimals)
    }

    /** Createtransaction is stricter than burn-price estimates — keep a buffer. */
    private fun bufferedNativeTrxFeeSun(feeSun: Long): Long {
        return maxOf(feeSun + feeSun / 2L, feeSun + 100_000L)
    }

    private data class UsdtPrepareBlock(
        val transfer: TronTransfer,
        val resources: TronResourcesEntity,
        val fee: MigrationTronFee,
        val feeOptions: MigrationUsdtFeeOptions,
        val usdtRequiredTrx: Coins,
    )

    private data class TronFeeSelection(
        val fee: MigrationTronFee,
        val options: MigrationUsdtFeeOptions,
    )

    private fun selectNativeTrxFee(
        migratableTrx: Coins,
        nativeRequiredTrx: Coins,
        rates: RatesEntity,
    ): TronFeeSelection {
        val trxFee = TrxFeeOption(
            amount = nativeRequiredTrx,
            enough = migratableTrx > nativeRequiredTrx,
            fiatAmount = rates.convert(TokenEntity.TRX.address, nativeRequiredTrx),
        )
        return TronFeeSelection(
            fee = trxFee.toMigrationFee(),
            options = MigrationUsdtFeeOptions(
                trx = trxFee.toMigrationFee(),
                trxEnough = trxFee.enough,
            ),
        )
    }

    private suspend fun selectUsdtFee(
        source: WalletEntity,
        transfer: TronTransfer,
        resources: TronResourcesEntity,
        trxBalance: Coins,
        rates: RatesEntity,
        chargesBalance: Int,
    ): TronFeeSelection = coroutineScope {
        val batteryDeferred = async {
            if (!WalletFeature.MigrationBattery.isEnabled) {
                null
            } else {
                estimateBatteryFee(source, transfer, resources, rates, chargesBalance)
            }
        }
        val trxDeferred = async { estimateTrxFee(resources, trxBalance, rates) }
        buildTronFeeSelection(
            batteryFee = batteryDeferred.await(),
            trxFee = trxDeferred.await(),
        )
    }

    private fun buildTronFeeSelection(
        batteryFee: BatteryFeeOption?,
        trxFee: TrxFeeOption,
    ): TronFeeSelection {
        val options = MigrationUsdtFeeOptions(
            battery = batteryFee?.toMigrationFee(),
            batteryEnough = batteryFee?.enough == true,
            trx = trxFee.toMigrationFee(),
            trxEnough = trxFee.enough,
        )
        val selected = batteryFee?.takeIf { it.enough }?.toMigrationFee()
            ?: trxFee.takeIf { it.enough }?.toMigrationFee()
            ?: trxFee.toMigrationFee()
        return TronFeeSelection(
            fee = selected,
            options = options,
        )
    }

    private data class BatteryFeeOption(
        val charges: Int,
        val enough: Boolean,
        val fiatAmount: Coins,
    ) {
        fun toMigrationFee() = MigrationTronFee.Battery(charges = charges, fiatAmount = fiatAmount)
    }

    private data class TrxFeeOption(
        val amount: Coins,
        val enough: Boolean,
        val fiatAmount: Coins,
    ) {
        fun toMigrationFee() = MigrationTronFee.Trx(amount = amount, fiatAmount = fiatAmount)
    }

    private suspend fun estimateBatteryFee(
        source: WalletEntity,
        transfer: TronTransfer,
        resources: TronResourcesEntity,
        rates: RatesEntity,
        chargesBalance: Int,
    ): BatteryFeeOption? {
        return runCatching {
            val estimation = api.tron.estimateBatteryCharges(
                transfer = transfer,
                resources = resources,
                auth = authorizationProvider.getAuthBy(source.id),
            )
            val charges = estimation.charges.coerceAtLeast(0)
            val config = batteryRepository.getConfig(source.network)
            val tonAmount = Coins.of(
                charges.toBigDecimal() * config.chargeCost.toBigDecimal(),
            )
            BatteryFeeOption(
                charges = charges,
                enough = charges > 0 && chargesBalance >= charges,
                fiatAmount = rates.convert(TokenEntity.TON.address, tonAmount),
            )
        }.getOrNull()
    }

    private suspend fun estimateTrxFee(
        resources: TronResourcesEntity,
        trxBalance: Coins,
        rates: RatesEntity,
    ): TrxFeeOption {
        val fee = api.tron.estimateTrxFee(resources).fee
        return TrxFeeOption(
            amount = fee,
            enough = trxBalance >= fee,
            fiatAmount = rates.convert(TokenEntity.TRX.address, fee),
        )
    }

    private data class WalletsCount(
        val count: Int,
        val candidateIds: Set<String>,
        val loadedAt: Long,
    )

    private suspend fun candidateWallets(): List<WalletEntity> {
        return accountRepository.getWallets().filter { wallet ->
            wallet.hasPrivateKey &&
                !wallet.testnet &&
                !wallet.tetra &&
                wallet.type != WalletType.Multichain
        }
    }

    private fun fetchMigrationValues(
        wallets: List<WalletEntity>,
        currency: WalletCurrency,
    ): Map<String, MigrationWalletValue>? {
        val response = withRetry {
            api.migration(TonNetwork.MAINNET).getMigrationWallets(
                currencies = listOf(currency.code),
                getBlockchainRawAccountsRequest = GetBlockchainRawAccountsRequest(
                    accountIds = wallets.map { it.accountId },
                ),
            )
        } ?: return null
        return response.wallets.associateBy { it.account }
    }

    private suspend fun loadRates(currency: WalletCurrency): RatesEntity {
        return ratesRepository.getRates(
            TonNetwork.MAINNET,
            currency,
            listOf(
                TokenEntity.TON.address,
                TokenEntity.USDT.address,
                TokenEntity.TRON_USDT.address,
                TokenEntity.TRX.address,
            ),
        )
    }

    private suspend fun loadTronBalances(
        wallets: List<WalletEntity>,
    ): Map<String, TronBalances> {
        if (api.getConfig(TonNetwork.MAINNET).flags.disableTron) {
            return emptyMap()
        }

        if (wallets.isEmpty()) {
            return emptyMap()
        }

        val addressByWalletId = resolveTronAddresses(wallets)
        if (addressByWalletId.isEmpty()) {
            return emptyMap()
        }

        val balancesByAddress = api.tron
            .getTronAccountBalancesBatch(addressByWalletId.values.distinct())
            .mapValues { (_, balances) ->
                TronBalances(
                    usdt = balances.usdt.value,
                    trx = balances.trx.value,
                )
            }
        return addressByWalletId.mapValues { (_, address) ->
            balancesByAddress[address] ?: TronBalances.Empty
        }
    }

    private suspend fun resolveTronAddresses(
        wallets: List<WalletEntity>,
    ): Map<String, String> {
        return buildMap {
            for (wallet in wallets) {
                val address = runCatching {
                    accountRepository.getTronAddress(wallet.id)
                }.getOrNull() ?: continue
                put(wallet.id, address)
            }
        }
    }

    private companion object {
        const val INSUFFICIENT_TON_GAS_CODE = 50000L
    }
}
