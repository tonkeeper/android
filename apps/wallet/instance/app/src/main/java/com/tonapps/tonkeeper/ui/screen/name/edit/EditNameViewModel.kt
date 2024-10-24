package com.tonapps.tonkeeper.ui.screen.name.edit

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take

class EditNameViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val accountRepository: AccountRepository
): BaseWalletVM(app) {

    fun save(name: String, emoji: CharSequence, color: Int) {
        accountRepository.editLabel(
            walletId = wallet.id,
            name = name,
            emoji = emoji,
            color = color
        )
    }
}