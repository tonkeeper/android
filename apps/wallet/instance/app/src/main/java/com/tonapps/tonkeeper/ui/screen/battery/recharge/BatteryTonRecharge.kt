package com.tonapps.tonkeeper.ui.screen.battery.recharge

import android.content.Context
import androidx.core.net.toUri
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.toGrams
import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.icu.Coins
import com.tonapps.log.L
import com.tonapps.tonkeeper.ui.screen.battery.recharge.entity.BatteryRechargeEvent
import com.tonapps.tonkeeper.ui.screen.battery.recharge.entity.RechargePackEntity
import com.tonapps.tonkeeper.ui.screen.send.transaction.BroadcastVia
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodType
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.passcode.PasscodeReason
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.ton.block.AddrStd
import java.math.BigDecimal

private const val TAG = "BatteryTonRecharge"

class BatteryTonRecharge(
    private val accountRepository: AccountRepository,
    private val batteryRepository: BatteryRepository,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
    private val api: API,
) {

    fun willBePaidManually(ton: AccountTokenEntity, batteryBalance: BatteryBalanceEntity): Boolean {
        return ton.balance.value >= Coins.of(0.1) || batteryBalance.balance > Coins.ZERO
    }

    suspend fun packs(
        wallet: WalletEntity,
        token: AccountTokenEntity,
        batteryConfig: BatteryConfigEntity,
        willBePaidManually: Boolean,
    ): List<RechargePackEntity> {
        val rechargeMethod = rechargeMethod(batteryConfig, token) ?: return emptyList()
        val packages = batteryConfig.packages.filter { it.charges > 0 }
        val maxCharges = packages.maxOfOrNull { it.charges } ?: return emptyList()
        val fiatRate = ratesRepository.getRates(wallet.network, settingsRepository.currency, token.address)
            .getRate(token.address)
        val serverConfig = api.getConfig(wallet.network)

        return packages.map { pack ->
            RechargePackEntity(
                type = pack.name,
                charges = pack.charges,
                maxCharges = maxCharges,
                rechargeMethod = rechargeMethod,
                fiatRate = fiatRate,
                token = token,
                config = serverConfig,
                willBePaidManually = willBePaidManually,
                currency = settingsRepository.currency,
                batteryConfig = batteryConfig
            )
        }.filter { it.isAvailableToBuy }
    }

    fun rechargeMethod(batteryConfig: BatteryConfigEntity, token: AccountTokenEntity): RechargeMethodEntity? {
        return batteryConfig.rechargeMethods.firstOrNull { pays(it, token) }
    }

    fun pays(method: RechargeMethodEntity, token: AccountTokenEntity): Boolean {
        return if (method.type == RechargeMethodType.TON) {
            token.isTon
        } else {
            method.jettonMaster == token.address
        }
    }

    suspend fun request(
        wallet: WalletEntity,
        token: AccountTokenEntity,
        packType: String?,
        customAmount: Coins?,
        recipient: AddrStd?,
    ): BatteryRechargeEvent {
        try {
            val config = batteryRepository.getConfig(wallet.network)
            val rechargeMethod = rechargeMethod(config, token) ?: return BatteryRechargeEvent.Error
            val batteryBalance = batteryRepository.getBalance(wallet)
            val batteryMaxInputAmount = rechargeMethod.fromTon(api.getConfig(wallet.network).batteryMaxInputAmount)

            val amount = packType?.let {
                val charges = config.packages.first { it.name == packType }.charges
                Coins.of(rechargeMethod.amountForCharges(charges, config.chargeCost), rechargeMethod.decimals)
            } ?: customAmount ?: return BatteryRechargeEvent.Error

            if (amount > batteryMaxInputAmount) {
                return BatteryRechargeEvent.MaxAmountError(
                    currency = token.symbol,
                    maxAmount = batteryMaxInputAmount
                )
            }

            val fundReceiver = config.fundReceiver ?: return BatteryRechargeEvent.Error
            val payload = wallet.contract.createBatteryBody(
                recipient,
                appliedPromo = batteryRepository.getAppliedPromo(wallet.network)
            )
            val validUntil = accountRepository.getValidUntil(wallet.network)

            val forceRelayer = when {
                token.isTon -> false
                batteryBalance.balance.value > BigDecimal.ZERO -> true
                rechargeMethod.minBootstrapValue != null -> {
                    amount.value >= rechargeMethod.minBootstrapValue!!.toBigDecimal()
                }

                else -> false
            }

            if (token.isTon) {
                val request = SignRequestEntity.Builder()
                    .setFrom(wallet.contract.address)
                    .setValidUntil(validUntil)
                    .addMessage(
                        RawMessageEntity(
                            addressValue = fundReceiver,
                            amount = amount.toBigInteger(),
                            stateInitValue = null,
                            payloadValue = payload.base64()
                        )
                    )
                    .setNetwork(wallet.network)
                    .build("https://battery.tonkeeper.com/".toUri())

                return BatteryRechargeEvent.Sign(request, forceRelayer)
            }

            // The confirm screen relays only for a wallet the battery supports; any other pays the gas in TON.
            val relayed = forceRelayer && wallet.supportsBattery

            val queryId = TransferEntity.newWalletQueryId()
            val customPayload = if (token.isRequestMinting) {
                api.getJettonCustomPayload(wallet.accountId, wallet.network, token.address)
            } else {
                null
            }

            val jettonPayload = TonTransferHelper.jetton(
                queryId = queryId,
                coins = amount.toGrams(),
                toAddress = AddrStd.parse(fundReceiver),
                responseAddress = wallet.contract.address,
                forwardPayload = payload,
                customPayload = customPayload?.customPayload
            )

            val request = SignRequestEntity.Builder()
                .setFrom(wallet.contract.address)
                .setValidUntil(validUntil)
                .setIgnoreInsufficientBalance(forceRelayer)
                .addMessage(
                    RawMessageEntity(
                        addressValue = token.balance.walletAddress,
                        amount = jettonTransferTon(config, token.address, relayed).toBigInteger(),
                        stateInitValue = null,
                        payloadValue = jettonPayload.base64()
                    )
                )
                .setNetwork(wallet.network)
                .build("https://battery.tonkeeper.com/".toUri())

            return BatteryRechargeEvent.Sign(request, forceRelayer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            return BatteryRechargeEvent.Error
        }
    }

    suspend fun sign(
        context: Context,
        wallet: WalletEntity,
        request: SignRequestEntity,
        forceRelayer: Boolean,
        utm: UtmParameters,
        isGift: Boolean,
    ): String = coroutineScope {
        val chargesBeforeRecharge = async { getChargesBeforeRecharge(wallet, isGift) }
        SendTransactionScreen.run(
            context = context,
            wallet = wallet,
            request = request,
            passcodeReason = PasscodeReason.TOPUP_BATTERY,
            forceRelayer = forceRelayer,
            broadcastVia = when (wallet.isMultichain) {
                true -> BroadcastVia.Battery
                else -> BroadcastVia.Default
            },
            utm = utm,
        ).also {
            chargesBeforeRecharge.await()?.let { refreshBalanceUntilRecharged(wallet, it) }
        }
    }

    private suspend fun getChargesBeforeRecharge(wallet: WalletEntity, isGift: Boolean): Int? {
        if (isGift) {
            return null
        }
        val charges = batteryRepository.getChargesBeforeRecharge(wallet)
        if (charges == null) {
            L.w(TAG, "No battery charges to compare the recharge against, skipping the refresh")
        }
        return charges
    }

    private fun refreshBalanceUntilRecharged(wallet: WalletEntity, chargesBeforeRecharge: Int) {
        Async.globalScope().launch {
            batteryRepository.refreshBalanceUntilRecharged(wallet, chargesBeforeRecharge)
        }
    }

    companion object {

        // TON a recharge the wallet pays for sends with the jetton; its excess comes back to the wallet.
        private val WALLET_PAID_JETTON_TON = Coins.of(0.1)

        /**
         * TON attached to the jetton transfer of a recharge. A relayed one attaches the battery's
         * transfer cost, as a relayed jetton send does: the relayer funds it and the excess goes back to
         * the battery. Attaching more would be read as that many charges spent.
         */
        fun jettonTransferTon(config: BatteryConfigEntity, jettonMaster: String, relayed: Boolean): Coins {
            return if (relayed) {
                config.transferCost(jettonMaster)
            } else {
                WALLET_PAID_JETTON_TON
            }
        }
    }
}
