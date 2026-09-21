package com.tonapps.wallet.data.battery

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.EmulateWithBatteryResult
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
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
import org.ton.cell.Cell

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

    suspend fun getBalance(
        wallet: WalletEntity,
        ignoreCache: Boolean = false,
    ): BatteryBalanceEntity = withContext(Dispatchers.IO) {
        if (wallet.network.isTetra) {
            return@withContext BatteryBalanceEntity.Empty
        }

        val balance = if (ignoreCache) {
            fetchBalance(wallet)
        } else {
            localDataSource.getBalance(wallet) ?: fetchBalance(wallet)
        }
        balance ?: BatteryBalanceEntity.Empty
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
        if (wallet.network.isTetra) {
            return BatteryBalanceEntity.Empty
        }

        val balance = remoteDataSource.fetchBalance(auth.getAuthBy(wallet.id), wallet.network)
            ?: return null
        localDataSource.setBalance(wallet, balance)
        _balanceUpdatedFlow.emit(Unit)
        return balance
    }

    suspend fun isIapDisabledByRefunds(wallet: WalletEntity): Boolean = withContext(Dispatchers.IO) {
        if (wallet.network.isTetra) {
            return@withContext false
        }
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

    suspend fun waitForChargesChanged(
        wallet: WalletEntity,
        fromCharges: Int,
        attempts: Int = CHARGES_POLL_ATTEMPTS,
        intervalMs: Long = CHARGES_POLL_INTERVAL_MS,
    ): Int? {
        repeat(attempts) {
            delay(intervalMs)
            val charges = try {
                fetchBalance(wallet)?.let { balance ->
                    BatteryMapper.convertToCharges(balance.balance, getConfig(wallet.network).chargeCost)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
            if (charges != null && charges != fromCharges) {
                return charges
            }
        }
        return null
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