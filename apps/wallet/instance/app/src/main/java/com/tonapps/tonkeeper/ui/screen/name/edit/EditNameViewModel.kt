package com.tonapps.tonkeeper.ui.screen.name.edit

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.tonkeeper.Wallet
import com.tonapps.tonkeeper.core.FirebaseHelper
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.worker.WidgetUpdaterWorker
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditNameViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
    private val walletId: String?,
) : BaseWalletVM(app) {

    val walletFlow: StateFlow<Wallet?> = combine(
        accountRepository.selectedWalletFlow,
        settingsRepository.walletPrefsChangedFlow,
        mcAccountRepository.refreshTrigger,
    ) { selected, _, _ ->
        withContext(Dispatchers.IO) {
            resolveWallet(selected)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    private val _labelFlow = MutableStateFlow<LabelEditor?>(null)
    val labelFlow = _labelFlow.asStateFlow().filterNotNull()

    init {
        walletFlow.filterNotNull().onEach { kind ->
            loadLabel(kind)
        }.launchIn(viewModelScope)
    }

    private suspend fun resolveWallet(selected: WalletEntity): Wallet? {
        val id = walletId ?: selected.id
        if (id.isBlank()) return null
        accountRepository.getWalletById(id)?.let { return Wallet.Legacy(it) }
        mcAccountRepository.getWallet(id)?.let { return Wallet.Multichain(it) }
        return null
    }

    private fun loadLabel(kind: Wallet) {
        when (kind) {
            is Wallet.Legacy -> {
                val wallet = kind.entity
                _labelFlow.value = LabelEditor(
                    name = wallet.label.name,
                    emoji = wallet.label.emoji.toString(),
                    color = wallet.label.color,
                )
            }
            is Wallet.Multichain -> {
                val mc = kind.entity
                _labelFlow.value = LabelEditor(
                    name = mc.name,
                    emoji = mc.emoji,
                    color = mc.color,
                )
            }
        }
    }

    fun save(name: String, emoji: CharSequence, color: Int) {
        if (_labelFlow.value == null) return
        FirebaseHelper.setTitleEmoji(emoji.toString())
        viewModelScope.launch {
            when (val kind = walletFlow.value ?: return@launch) {
                is Wallet.Legacy -> {
                    accountRepository.editLabel(
                        walletId = kind.id,
                        name = name,
                        emoji = emoji,
                        color = color
                    )
                }
                is Wallet.Multichain -> {
                    mcAccountRepository.updateWalletLabel(kind.id, name, emoji.toString(), color)
                }
            }
            WidgetUpdaterWorker.update(context)
        }
    }

    data class LabelEditor(
        val name: String,
        val emoji: String,
        val color: Int,
    )
}
