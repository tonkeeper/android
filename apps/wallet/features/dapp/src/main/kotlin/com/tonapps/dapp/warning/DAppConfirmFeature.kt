package com.tonapps.dapp.warning

import android.net.Uri
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.settings.SettingsRepository

sealed interface DAppConfirmAction : MviAction {
    object Init : DAppConfirmAction
    data class UpdateCheckbox(val checked: Boolean) : DAppConfirmAction
}

data class DAppConfirmState(
    val host: String = "",
    val iconUrl: String = "",
    val name: String = ""
) : MviState

class DAppConfirmViewState(
    val global: MviProperty<DAppConfirmState>
) : MviViewState

data class DAppConfirmData(
    val walletId: String,
    val app: AppEntity,
    val dAppUrl: Uri
)

class DAppConfirmFeature(
    private val data: DAppConfirmData,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
) : MviFeature<DAppConfirmAction, DAppConfirmState, DAppConfirmViewState>(
    initState = DAppConfirmState(),
    initAction = DAppConfirmAction.Init
) {

    override fun createViewState(): DAppConfirmViewState {
        return buildViewState {
            DAppConfirmViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: DAppConfirmAction) {
        when (action) {
            is DAppConfirmAction.Init -> initialize()
            is DAppConfirmAction.UpdateCheckbox -> updateCheckbox(action.checked)
        }
    }

    private fun initialize() {
        setState {
            copy(
                host = data.dAppUrl.host ?: data.app.host,
                iconUrl = data.app.iconUrl,
                name = data.app.name
            )
        }
    }

    private suspend fun updateCheckbox(checked: Boolean) {
        val wallet = accountRepository.getWalletById(data.walletId) ?: return // TODO Show Error
        settingsRepository.setDAppOpenConfirm(wallet.id, data.app.host, !checked)
    }
}
