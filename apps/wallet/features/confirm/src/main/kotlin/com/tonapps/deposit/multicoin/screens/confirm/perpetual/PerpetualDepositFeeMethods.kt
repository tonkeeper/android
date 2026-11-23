package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.core.flags.WalletFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationFeeMethods
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.coinValue
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.log.L
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException

class PerpetualDepositFeeMethods(
    private val feeBuilder: TxFeeBuilder,
    private val settingsRepository: SettingsRepository,
    private val batteryRepository: BatteryRepository,
    private val isBatteryFeeEnabled: () -> Boolean = { WalletFeature.PerpsBatteryFee.isEnabled },
) : OperationFeeMethods {

    override suspend fun build(
        request: ConfirmRequest,
        position: AccountWithDetails,
        energy: AccountWithDetails,
        estimated: Fee,
    ): TxFeeState {
        val native = TxFeeState(energy = energy, estimated = estimated)
        val deposit = request.type as? ConfirmType.PerpetualDeposit ?: return native
        if (!deposit.payableByRelayer) {
            return native.copy(options = listOf(TxFee(account = FeeAccount.Chain(energy), fee = estimated)))
        }
        if (!deposit.quote.crossChain) {
            return native
        }
        val payload = deposit.operation.payloads.singleOrNull() ?: return native
        val spentAmount = if (position.asset.value is Asset.Coin) {
            payload.meta.coinValue()
        } else {
            deposit.amount
        }
        val state = feeBuilder.buildOperation(
            account = position,
            energy = energy,
            walletId = request.data.walletId,
            payload = payload,
            spentAmount = spentAmount,
            estimated = estimated,
        )
        if (isBatteryFeeEnabled()) {
            return state
        }
        return state.copy(options = state.options.filterNot { it.isBattery }, shortBattery = null)
    }

    override fun remember(walletId: String, option: TxFee) {
        settingsRepository.setPreferredFeeMethod(walletId, option.preferredMethod)
    }

    override fun payment(preview: ConfirmRequest): ConfirmRequest? {
        val deposit = preview.type as? ConfirmType.PerpetualDeposit ?: return null
        val operation = deposit.paymentOperation ?: return null
        val data = preview.data
        return ConfirmRequest(
            data = CommonTransactionData(
                assetId = data.assetId,
                walletId = data.walletId,
                nonce = data.nonce,
                expiresAt = data.expiresAt,
            ),
            type = deposit.copy(operation = operation, paymentOperation = null),
        )
    }

    suspend fun chargeCostInTon(): BigDecimal? {
        return try {
            BigDecimal.parseString(batteryRepository.getConfig(TonNetwork.MAINNET).chargeCost)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            null
        }
    }

    suspend fun canRelayShortfall(
        request: ConfirmRequest,
        position: AccountWithDetails,
        energy: AccountWithDetails,
        estimated: Fee,
    ): Boolean {
        return build(request, position, energy, estimated).hasRelayedOption
    }
}
