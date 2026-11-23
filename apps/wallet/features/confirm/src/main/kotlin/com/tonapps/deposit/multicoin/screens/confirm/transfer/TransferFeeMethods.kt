package com.tonapps.deposit.multicoin.screens.confirm.transfer

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationFeeMethods
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.coinValue
import com.tonapps.deposit.multicoin.screens.confirm.fee.GaslessRelayer
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.settings.SettingsRepository

/**
 * The ways a send pays its fee besides the chain's coin: the battery and gasless. The operation built for
 * the send goes to the gasless relayer as it states itself, as a transfer held to the recipient and the
 * amount the [screen] asked for; what the relayer refuses is paid in the coin alone.
 */
internal class TransferFeeMethods(
    private val screen: ConfirmRequest,
    private val feeBuilder: TxFeeBuilder,
    private val settingsRepository: SettingsRepository,
) : OperationFeeMethods {

    // A send of a token is what a relayer is for: the wallet may hold none of the coin the chain prices with.
    override val buildsUnpriced: Boolean = true

    override suspend fun build(
        request: ConfirmRequest,
        position: AccountWithDetails,
        energy: AccountWithDetails,
        estimated: Fee,
    ): TxFeeState {
        val native = TxFeeState(energy = energy, estimated = estimated)
        val transfer = request.type as ConfirmType.Transfer
        val operation = transfer.operation
        if (operation.payloads.isEmpty()) {
            return native
        }

        // A send of TON is paid in TON for now: the battery charges for the TON it sends, not for its gas alone.
        val asset = position.asset.value
        if (asset is Asset.Coin && asset.chain.isTvm) {
            return native
        }

        // Held to what the user asked for: a refit sends less, which the intent allows, never more.
        val asked = screen.asked(position.value)

        val isMax = transfer.isMax == true
        val methods = feeBuilder.buildSwapOperation(
            account = position,
            energy = energy,
            walletId = screen.data.walletId,
            payloads = operation.payloads,
            sold = asked.amount,
            // A whole coin balance leaves the wallet's coin nothing past the amount for the fee.
            shrinkToFit = isMax && position.asset.value is Asset.Coin,
            estimated = estimated,
            purpose = GaslessRelayer.Purpose.transfer(asked.to),
        )

        return withAttached(methods, position, operation.payloads)
    }

    // The coin a token's send carries rides as its gas, a jetton transfer's forward TON: a relayer attaches
    // it with the fee, and the wallet's own send needs it on top of the fee.
    private fun withAttached(methods: TxFeeState, position: AccountWithDetails, payloads: List<Payload>): TxFeeState {
        if (position.asset.value !is Asset.Token) {
            return methods
        }
        val attached = payloads.fold(BigInteger.ZERO) { sum, payload -> sum + payload.meta.coinValue() }
        return methods.copy(attachedEnergy = attached)
    }

    override fun remember(walletId: String, option: TxFee) {
        settingsRepository.setPreferredFeeMethod(walletId, option.preferredMethod)
    }
}
