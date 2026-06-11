package com.tonapps.tonkeeper.helper

import com.tonapps.icu.Coins
import com.tonapps.deposit.usecase.emulation.Emulated
import com.tonapps.deposit.usecase.emulation.EmulationUseCase
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.MessageBodyEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BatteryHelper {

    suspend fun getBatteryCharges(
        wallet: WalletEntity,
        accountRepository: AccountRepository,
        batteryRepository: BatteryRepository
    ): Int = withContext(Dispatchers.IO) {
        accountRepository.requestTonProofToken(wallet)?.let {
            batteryRepository.getCharges(it, wallet.publicKey, wallet.network, true)
        } ?: 0
    }

    suspend fun getBalance(
        wallet: WalletEntity,
        accountRepository: AccountRepository,
        batteryRepository: BatteryRepository
    ): Coins {
        val tonProof = accountRepository.requestTonProofToken(wallet) ?: return Coins.ZERO
        val entity = batteryRepository.getBalance(
            tonProofToken = tonProof,
            publicKey = wallet.publicKey,
            network = wallet.network,
            ignoreCache = true
        )
        return entity.balance
    }

    suspend fun emulation(
        wallet: WalletEntity,
        message: MessageBodyEntity,
        emulationUseCase: EmulationUseCase,
        accountRepository: AccountRepository,
        batteryRepository: BatteryRepository,
        forceRelayer: Boolean = false,
        params: Boolean
    ): Emulated? {
        val chargesBalance = getBatteryCharges(wallet, accountRepository, batteryRepository)
        val batteryConfig = batteryRepository.getConfig(wallet.network)

        val emulated = emulationUseCase(
            message = message,
            useBattery = true,
            forceRelayer = forceRelayer,
            params = params
        )

        val charges = BatteryMapper.calculateChargesAmount(
            emulated.extra.value.value,
            batteryConfig.chargeCost
        )
        return if (charges > chargesBalance && !forceRelayer) null else emulated
    }

    suspend fun isBatteryIsEnabledTx(
        wallet: WalletEntity,
        txType: BatteryTransaction,
        settingsRepository: SettingsRepository,
        accountRepository: AccountRepository,
        batteryRepository: BatteryRepository
    ): Boolean = withContext(Dispatchers.IO) {
        if (!settingsRepository.batteryIsEnabledTx(wallet.accountId, txType)) {
            return@withContext false
        }
        getBalance(wallet, accountRepository, batteryRepository).isPositive
    }

}