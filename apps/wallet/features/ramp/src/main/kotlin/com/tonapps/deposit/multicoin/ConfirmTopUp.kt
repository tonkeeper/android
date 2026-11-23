package com.tonapps.deposit.multicoin

import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.multicoin.analytics.asDepositFlowFrom
import com.tonapps.deposit.screens.qr.QrAssetFragment
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.log.L
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAccounts
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAssets
import kotlinx.coroutines.CancellationException
import uikit.base.BaseFragment

suspend fun confirmTopUpScreen(
    request: ConfirmRequest,
    assetId: String,
    legacySwapAccounts: LegacySwapAccounts,
): BaseFragment {
    val legacyWallet = try {
        legacySwapAccounts.find(request.data.walletId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        L.e(e)
        null
    }
    val legacyToken = LegacySwapAssets.token(assetId)
    return if (legacyWallet != null && legacyToken != null) {
        QrAssetFragment.newInstance(
            token = legacyToken,
            walletId = legacyWallet.id,
            analyticsFrom = request.topUpFrom(),
        )
    } else {
        DepositMulticoinFragment.create(
            walletId = request.data.walletId,
            rampType = RampType.RampOn,
            initial = DepositMulticoinRoutes.Buy(assetId),
            analyticsFrom = request.topUpFrom(),
        )
    }
}

private fun ConfirmRequest.topUpFrom(): DepositFlowFrom {
    if (context is ConfirmContext.Dapp) {
        return DepositFlowFrom.Walletconnect
    }
    return initiator.asDepositFlowFrom()
}
