package com.tonapps.wallet.data.battery

import android.content.Context
import android.util.Log
import androidx.collection.ArrayMap
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.filterList
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.battery.source.LocalDataSource
import com.tonapps.wallet.data.battery.source.RemoteDataSource
import io.tonapi.models.MessageConsequences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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
            getConfig(false, ignoreCache = true)
        }
    }

    suspend fun getRechargeMethodByJetton(
        testnet: Boolean,
        jetton: String
    ): RechargeMethodEntity? {
        val rechargeMethods = getConfig(testnet).rechargeMethods.filter { it.supportRecharge }
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
        testnet: Boolean,
        ignoreCache: Boolean = false
    ): BatteryConfigEntity = withContext(Dispatchers.IO) {
        if (ignoreCache) {
            fetchConfig(testnet)
        } else {
            localDataSource.getConfig(testnet) ?: fetchConfig(testnet)
        }
    }

    private suspend fun fetchConfig(testnet: Boolean): BatteryConfigEntity {
        val config = remoteDataSource.fetchConfig(testnet) ?: return BatteryConfigEntity.Empty
        localDataSource.setConfig(testnet, config)
        return config
    }

    suspend fun getBalance(
        tonProofToken: String,
        publicKey: PublicKeyEd25519,
        testnet: Boolean,
        ignoreCache: Boolean = false,
    ): BatteryBalanceEntity = withContext(Dispatchers.IO) {
        val balance = if (ignoreCache) {
            fetchBalance(publicKey, tonProofToken, testnet)
        } else {
            localDataSource.getBalance(publicKey, testnet) ?: fetchBalance(
                publicKey,
                tonProofToken,
                testnet
            )
        }
        balance
    }

    suspend fun getCharges(
        tonProofToken: String,
        publicKey: PublicKeyEd25519,
        testnet: Boolean,
        ignoreCache: Boolean = false,
    ): Int = withContext(Dispatchers.IO) {
        val balance = getBalance(tonProofToken, publicKey, testnet, ignoreCache)
        val charges = BatteryMapper.convertToCharges(balance.balance, api.config.batteryMeanFees)
        charges
    }

    private suspend fun fetchBalance(
        publicKey: PublicKeyEd25519,
        tonProofToken: String,
        testnet: Boolean
    ): BatteryBalanceEntity {
        val balance = remoteDataSource.fetchBalance(tonProofToken, testnet)
            ?: return BatteryBalanceEntity.Empty
        localDataSource.setBalance(publicKey, testnet, balance)
        _balanceUpdatedFlow.emit(Unit)
        return balance
    }

    fun refreshBalanceDelay(
        publicKey: PublicKeyEd25519,
        tonProofToken: String,
        testnet: Boolean
    ) {
        scope.launch(Dispatchers.IO) {
            delay(10000)
            fetchBalance(publicKey, tonProofToken, testnet)
        }
    }

    suspend fun emulate(
        tonProofToken: String,
        publicKey: PublicKeyEd25519,
        testnet: Boolean,
        boc: Cell,
        forceRelayer: Boolean = false,
        safeModeEnabled: Boolean,
    ): Pair<MessageConsequences, Boolean>? = withContext(Dispatchers.IO) {

        val balance = getBalance(
            tonProofToken = tonProofToken,
            publicKey = publicKey,
            testnet = testnet
        ).balance

        if (!forceRelayer && !balance.isPositive) {
            throw IllegalStateException("Zero balance")
        }

        api.emulateWithBattery(tonProofToken, boc, testnet, safeModeEnabled)
    }

    suspend fun getAppliedPromo(
        testnet: Boolean,
    ): String? = withContext(Dispatchers.IO) {
        localDataSource.getAppliedPromo(testnet)
    }

    suspend fun setAppliedPromo(
        testnet: Boolean,
        promo: String?,
    ) = withContext(Dispatchers.IO) {
        localDataSource.setAppliedPromo(testnet, promo)
    }

}