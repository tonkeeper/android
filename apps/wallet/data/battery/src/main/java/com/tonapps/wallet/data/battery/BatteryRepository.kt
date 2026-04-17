package com.tonapps.wallet.data.battery

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.EmulateWithBatteryResult
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.battery.source.LocalDataSource
import com.tonapps.wallet.data.battery.source.RemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pub.PublicKeyEd25519
import org.ton.cell.Cell

class BatteryRepository(
    context: Context,
    private val api: API,
    private val scope: CoroutineScope,
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
        tonProofToken: String,
        publicKey: PublicKeyEd25519,
        network: TonNetwork,
        ignoreCache: Boolean = false,
    ): BatteryBalanceEntity = withContext(Dispatchers.IO) {
        if (network.isTetra) {
            return@withContext BatteryBalanceEntity.Empty
        }

        val balance = if (ignoreCache) {
            fetchBalance(publicKey, tonProofToken, network)
        } else {
            localDataSource.getBalance(publicKey, network) ?: fetchBalance(
                publicKey,
                tonProofToken,
                network
            )
        }
        balance
    }

    suspend fun getCharges(
        tonProofToken: String,
        publicKey: PublicKeyEd25519,
        network: TonNetwork,
        ignoreCache: Boolean = false,
    ): Int = withContext(Dispatchers.IO) {
        val balance = getBalance(tonProofToken, publicKey, network, ignoreCache)
        val config = getConfig(network, ignoreCache)
        val charges = BatteryMapper.convertToCharges(balance.balance, config.chargeCost)
        charges
    }

    private suspend fun fetchBalance(
        publicKey: PublicKeyEd25519,
        tonProofToken: String,
        network: TonNetwork
    ): BatteryBalanceEntity {
        if (network.isTetra) {
            return BatteryBalanceEntity.Empty
        }

        val balance = remoteDataSource.fetchBalance(tonProofToken, network)
            ?: return BatteryBalanceEntity.Empty
        localDataSource.setBalance(publicKey, network, balance)
        _balanceUpdatedFlow.emit(Unit)
        return balance
    }

    fun refreshBalanceDelay(
        publicKey: PublicKeyEd25519,
        tonProofToken: String,
        network: TonNetwork
    ) {
        scope.launch(Dispatchers.IO) {
            delay(10000)
            fetchBalance(publicKey, tonProofToken, network)
        }
    }

    suspend fun emulate(
        tonProofToken: String,
        publicKey: PublicKeyEd25519,
        network: TonNetwork,
        boc: Cell,
        forceRelayer: Boolean = false,
        safeModeEnabled: Boolean,
    ): EmulateWithBatteryResult? = withContext(Dispatchers.IO) {

        val balance = getBalance(
            tonProofToken = tonProofToken,
            publicKey = publicKey,
            network = network
        ).balance

        if (!forceRelayer && !balance.isPositive) {
            throw IllegalStateException("Zero balance")
        }

        api.emulateWithBattery(tonProofToken, boc, network, safeModeEnabled)
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