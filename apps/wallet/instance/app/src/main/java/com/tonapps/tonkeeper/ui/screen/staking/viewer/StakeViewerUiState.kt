package com.tonapps.tonkeeper.ui.screen.staking.viewer

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.entity.EthenaEntity

data class StakeViewerUiState(
    val items: List<Item>,
    val actions: StakeViewerActions? = null,
)

data class StakeViewerActions(
    val wallet: WalletEntity,
    val stakeDisabled: Boolean = false,
    val unstakeDisabled: Boolean = false,
    val poolAddress: String? = null,
    val ethenaMethod: EthenaEntity.Method? = null,
    val utm: UtmParameters = UtmParameters.Empty,
) {
    val iconRes: Int?
        get() {
            return when (ethenaMethod?.type) {
                EthenaEntity.Method.Type.STONFI -> R.drawable.stonfi
                EthenaEntity.Method.Type.AFFLUENT -> R.drawable.affluent
                null -> null
            }
        }
}