package com.tonapps.wallet.data.battery

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.api.entity.EmulateWithBatteryResult
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.BatteryPurchaseQuote
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.battery.entity.GaslessChainsEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodsEntity
import com.tonapps.wallet.data.battery.source.LocalDataSource
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.battery.source.RemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.batteryapi.models.PurchaseBatteryEmulateRequest
import org.ton.cell.Cell
import java.math.BigInteger

private const val CHARGES_POLL_ATTEMPTS = 20
private const val CHARGES_POLL_INTERVAL_MS = 15_000L

class BatteryRepository(
    context: Context,
    private val api: API,
    private val scope: CoroutineScope,
    private val auth: AuthorizationProvider,
) {
    private val localDataSource = LocalDataSource(context)
    private val remoteDataSource = RemoteDataSource(api)

    private val _balanceUpdatedFlow = MutableEffectFlow<Unit>()
    val balanceUpdatedFlow = _balanceUpdatedFlow.asSharedFlow()

    init {
        _balanceUpdatedFlow.tryEmit(Unit)
        scope.launch(Dispatchers.IO) {
            getConfig(TonNetwork.MAINNET, ignoreCache = true)
        }
        scope.launch(Dispatchers.IO) {
            getGaslessChains(TonNetwork.MAINNET, ignoreCache = true)
        }
    }

    suspend fun getRechargeMethodByJetton(
        network: TonNetwork,
        jetton: String
    ): RechargeMethodEntity? {
        val rechargeMethods = getConfig(network).rechargeMethods.filter { it.supportRecharge }
        if (rechargeMethods.isEmpty()) {
            return null
        }
        return rechargeMethods.firstOrNull {
            it.symbol.equals(
                jetton,
                ignoreCase = true
            ) || it.jettonMaster?.equalsAddress(jetton) == true
        }
    }

    suspend fun getConfig(
        network: TonNetwork,
        ignoreCache: Boolean = false
    ): BatteryConfigEntity = withContext(Dispatchers.IO) {
        if (ignoreCache) {
            fetchConfig(network)
        } else {
            localDataSource.getConfig(network) ?: fetchConfig(network)
        }
    }

    private suspend fun fetchConfig(network: TonNetwork): BatteryConfigEntity {
        val config = remoteDataSource.fetchConfig(network) ?: return BatteryConfigEntity.Empty
        localDataSource.setConfig(network, config)
        return config
    }

    /**
     * The methods a recharge can be paid with on [network]: TON's, and for a [multichain] wallet the other
     * chains' as well. Only those [RechargeMethodEntity.supportRecharge] says so of; the ones listed for gasless
     * fees alone stay in [getConfig]. A multichain list the battery cannot give falls back to TON's.
     */
    suspend fun getRechargeMethods(
        network: TonNetwork,
        multichain: Boolean,
        ignoreCache: Boolean = false,
    ): List<RechargeMethodEntity> = withContext(Dispatchers.IO) {
        val methods = if (multichain) {
            getMultichainRechargeMethods(network, ignoreCache) ?: getConfig(network).rechargeMethods
        } else {
            getConfig(network, ignoreCache).rechargeMethods
        }
        methods.filter { it.supportRecharge }
    }

    /** What [getRechargeMethods] last stored, without going to the battery; null when nothing is stored yet. */
    suspend fun getCachedRechargeMethods(
        network: TonNetwork,
        multichain: Boolean,
    ): List<RechargeMethodEntity>? = withContext(Dispatchers.IO) {
        val methods = if (multichain) {
            localDataSource.getMultichainRechargeMethods(network)?.methods
        } else {
            localDataSource.getConfig(network)?.rechargeMethods
        }
        methods?.filter { it.supportRecharge }
    }

    private suspend fun getMultichainRechargeMethods(
        network: TonNetwork,
        ignoreCache: Boolean,
    ): List<RechargeMethodEntity>? {
        val cached = localDataSource.getMultichainRechargeMethods(network).takeUnless { ignoreCache }
        val entity = cached ?: fetchMultichainRechargeMethods(network) ?: localDataSource.getMultichainRechargeMethods(network)
        return entity?.methods
    }

    private suspend fun fetchMultichainRechargeMethods(network: TonNetwork): RechargeMethodsEntity? {
        val entity = try {
            remoteDataSource.fetchMultichainRechargeMethods(network)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        } ?: return null
        localDataSource.setMultichainRechargeMethods(network, entity)
        return entity
    }

    /**
     * What buying with [amount] smallest units of [assetId] from [senderAddress] on [chainId] would add, for
     * [walletId]'s battery. Throws what the battery answers: a 4XX is a `GaslessV2Error`.
     */
    suspend fun estimatePurchase(
        walletId: String,
        chainId: String,
        assetId: String,
        senderAddress: String,
        amount: BigInteger,
        promo: String?,
    ): BatteryPurchaseQuote = withContext(Dispatchers.IO) {
        val request = purchaseRequest(chainId, assetId, senderAddress, amount, promo)
        BatteryPurchaseQuote.of(api.purchaseBatteryEstimate(auth.getAuthBy(walletId), request))
    }

    /** The purchase [estimatePurchase] prices, with the operation to sign before its [BatteryPurchaseQuote.expiresAt]. */
    suspend fun emulatePurchase(
        walletId: String,
        chainId: String,
        assetId: String,
        senderAddress: String,
        amount: BigInteger,
        promo: String?,
    ): BatteryPurchaseQuote = withContext(Dispatchers.IO) {
        val request = purchaseRequest(chainId, assetId, senderAddress, amount, promo)
        BatteryPurchaseQuote.of(api.purchaseBatteryEmulate(auth.getAuthBy(walletId), request))
    }

    private fun purchaseRequest(
        chainId: String,
        assetId: String,
        senderAddress: String,
        amount: BigInteger,
        promo: String?,
    ) = PurchaseBatteryEmulateRequest(
        chainId = chainId,
        senderAddress = senderAddress,
        assetId = assetId,
        amount = amount.toString(),
        promo = promo?.takeIf { it.isNotBlank() },
    )

    /**
     * The chains the gasless relayer carries, by its `chain_id` (`eth/mainnet`). Read from the cache, which
     * a launch refreshes; when the relayer cannot be reached, the last list it gave stands.
     */
    suspend fun getGaslessChains(
        network: TonNetwork,
        ignoreCache: Boolean = false,
    ): Set<String> = withContext(Dispatchers.IO) {
        val entity = if (ignoreCache) {
            fetchGaslessChains(network) ?: localDataSource.getGaslessChains(network)
        } else {
            localDataSource.getGaslessChains(network) ?: fetchGaslessChains(network)
        }
        entity?.chainIds?.toSet().orEmpty()
    }

    private suspend fun fetchGaslessChains(network: TonNetwork): GaslessChainsEntity? {
        val entity = remoteDataSource.fetchGaslessChains(network) ?: return null
        localDataSource.setGaslessChains(network, entity)
        return entity
    }

    suspend fun getBalance(
        wallet: WalletEntity,
        ignoreCache: Boolean = false,
    ): BatteryBalanceEntity = withContext(Dispatchers.IO) {
        val balance = if (ignoreCache) {
            fetchBalance(wallet)
        } else {
            localDataSource.getBalance(wallet) ?: fetchBalance(wallet)
        }
        balance ?: BatteryBalanceEntity.Empty
    }

    suspend fun getCachedBalance(wallet: WalletEntity): BatteryBalanceEntity? = withContext(Dispatchers.IO) {
        localDataSource.getBalance(wallet)
    }

    suspend fun getCharges(
        wallet: WalletEntity,
        ignoreCache: Boolean = false,
    ): Int = withContext(Dispatchers.IO) {
        val balance = getBalance(wallet, ignoreCache)
        val config = getConfig(wallet.network, ignoreCache)
        val charges = BatteryMapper.convertToCharges(balance.balance, config.chargeCost)
        charges
    }

    private suspend fun fetchBalance(wallet: WalletEntity): BatteryBalanceEntity? {
        return fetchBalance(wallet, auth.getAuthBy(wallet.id))
    }

    suspend fun fetchBalance(wallet: WalletEntity, authorization: Authorization): BatteryBalanceEntity? {
        val balance = remoteDataSource.fetchBalance(authorization, wallet.network)
            ?: return null
        localDataSource.setBalance(wallet, balance)
        _balanceUpdatedFlow.emit(Unit)
        return balance
    }

    suspend fun isIapDisabledByRefunds(wallet: WalletEntity): Boolean = withContext(Dispatchers.IO) {
        remoteDataSource.fetchPurchases(auth.getAuthBy(wallet.id), wallet.network)
            ?.let(BatteryMapper::isIapDisabledByRefunds)
            ?: false
    }

    fun refreshBalanceDelay(wallet: WalletEntity) {
        scope.launch(Dispatchers.IO) {
            delay(10000)
            fetchBalance(wallet)
        }
    }

    suspend fun refreshBalanceUntilRecharged(
        wallet: WalletEntity,
        chargesBeforeRecharge: Int,
    ) = withContext(Dispatchers.IO) {
        pollCharges(wallet, CHARGES_POLL_ATTEMPTS, CHARGES_POLL_INTERVAL_MS) { charges ->
            charges > chargesBeforeRecharge
        }
    }

    suspend fun waitForChargesChanged(
        wallet: WalletEntity,
        fromCharges: Int,
        attempts: Int = CHARGES_POLL_ATTEMPTS,
        intervalMs: Long = CHARGES_POLL_INTERVAL_MS,
    ): Int? = pollCharges(wallet, attempts, intervalMs) { charges -> charges != fromCharges }

    private suspend fun pollCharges(
        wallet: WalletEntity,
        attempts: Int,
        intervalMs: Long,
        isSettled: (charges: Int) -> Boolean,
    ): Int? {
        repeat(attempts) {
            delay(intervalMs)
            val charges = fetchCharges(wallet)
            if (charges != null && isSettled(charges)) {
                return charges
            }
        }
        return null
    }

    suspend fun getChargesBeforeRecharge(wallet: WalletEntity): Int? =
        fetchCharges(wallet) ?: getCachedCharges(wallet)

    private suspend fun getCachedCharges(wallet: WalletEntity): Int? = withContext(Dispatchers.IO) {
        val balance = localDataSource.getBalance(wallet) ?: return@withContext null
        val config = localDataSource.getConfig(wallet.network) ?: return@withContext null
        BatteryMapper.convertToCharges(balance.balance, config.chargeCost)
    }

    suspend fun fetchCharges(wallet: WalletEntity): Int? = withContext(Dispatchers.IO) {
        try {
            fetchBalance(wallet)?.let { balance ->
                BatteryMapper.convertToCharges(balance.balance, getConfig(wallet.network).chargeCost)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun emulate(
        wallet: WalletEntity,
        boc: Cell,
        forceRelayer: Boolean = false,
        safeModeEnabled: Boolean,
    ): EmulateWithBatteryResult? = withContext(Dispatchers.IO) {
        val balance = getBalance(wallet).balance

        if (!forceRelayer && !balance.isPositive) {
            throw IllegalStateException("Zero balance")
        }

        api.emulateWithBattery(
            auth = auth.getAuthBy(wallet.id),
            cell = boc,
            network = wallet.network,
            safeModeEnabled = safeModeEnabled,
        )
    }

    suspend fun getAppliedPromo(
        network: TonNetwork,
    ): String? = withContext(Dispatchers.IO) {
        localDataSource.getAppliedPromo(network)
    }

    suspend fun setAppliedPromo(
        network: TonNetwork,
        promo: String?,
    ) = withContext(Dispatchers.IO) {
        localDataSource.setAppliedPromo(network, promo)
    }

}