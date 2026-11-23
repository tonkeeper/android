package com.tonapps.deposit.multicoin.screens.confirm

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationFeeMethods
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.settings.SettingsRepository

/**
 * The ways a `/v3` swap's fee may be paid: the battery or a gasless relayer besides the chain's coin.
 * The operation's payloads go to the gasless relayer as they state themselves; what it refuses is paid
 * in the coin alone.
 */
internal class SwapOperationFeeMethods(
    private val screen: ConfirmRequest,
    private val feeBuilder: TxFeeBuilder,
    private val settingsRepository: SettingsRepository,
) : OperationFeeMethods {

    // A sale of a token is what a relayer is for: the wallet may hold none of the coin the chain prices with.
    override val buildsUnpriced: Boolean = true

    override suspend fun build(
        request: ConfirmRequest,
        position: AccountWithDetails,
        energy: AccountWithDetails,
        estimated: Fee,
    ): TxFeeState {
        val type = request.type as ConfirmType.SwapOperation
        if (type.operation.payloads.isEmpty()) {
            return TxFeeState(energy = energy, estimated = estimated)
        }

        return feeBuilder.buildSwapOperation(
            account = position,
            energy = energy,
            walletId = screen.data.walletId,
            payloads = type.operation.payloads,
            sold = type.swap.sourceAmount,
            shrinkToFit = type.swap.isMax == true,
            estimated = estimated,
        )
    }

    override fun remember(walletId: String, option: TxFee) {
        settingsRepository.setPreferredFeeMethod(walletId, option.preferredMethod)
    }
}
