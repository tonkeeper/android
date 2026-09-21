package com.tonapps.deposit.multicoin.screens.confirm

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.deposit.multicoin.screens.confirm.engine.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.engine.GaslessSender
import com.tonapps.mvi.flow.mapLatestCatching
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.dapps.wc.WcRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository

/**
 * Confirm feature for non-swap requests: transfers, contract calls and message signing.
 */
class TxConfirmFeature(
    request: ConfirmRequest,
    provider: ChainKitProvider,
    accountRepo: McAccountRepository,
    postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
    passcodeManager: PasscodeManager,
    wcRepository: WcRepository,
    feeBuilder: TxFeeBuilder,
    gaslessSender: GaslessSender,
    settingsRepository: SettingsRepository,
) : BaseConfirmFeature(
    request = request,
    provider = provider,
    accountRepo = accountRepo,
    postTransactionRefreshSchedule = postTransactionRefreshSchedule,
    passcodeManager = passcodeManager,
    wcRepository = wcRepository,
    feeBuilder = feeBuilder,
    gaslessSender = gaslessSender,
    settingsRepository = settingsRepository,
) {

    override val pendingTx = refreshToken
        .mapLatestCatching(
            onError = { onPrepareFailed(it) },
            onFinally = { pendingLoader.tryEmit(false) },
        ) {
            pendingLoader.tryEmit(true)
            prepareTracked {
                when (val type = request.type) {
                    is ConfirmType.Message -> prepareMessage(type)
                    is ConfirmType.Call -> prepareCall(type)
                    is ConfirmType.Transfer -> prepareTransaction(type)
                    is ConfirmType.Swap -> throw IllegalStateException("Swap is not handled by TxConfirmFeature")
                }
            }
        }
        .keepLastPrepared()
}
