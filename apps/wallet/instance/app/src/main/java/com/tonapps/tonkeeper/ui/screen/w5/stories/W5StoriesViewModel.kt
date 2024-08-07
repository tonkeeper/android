package com.tonapps.tonkeeper.ui.screen.w5.stories

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

class W5StoriesViewModel(
    private val accountRepository: AccountRepository,
    private val passcodeManager: PasscodeManager,
    private val backupRepository: BackupRepository,
): ViewModel() {

    private val stories = StoryEntity.all

    private val _storyFlow = MutableEffectFlow<StoryEntity>()
    val storyFlow = _storyFlow.asSharedFlow()

    private var timerJob: Job? = null
    private var currentIndex = -1

    init {
        nextStory()
    }

    fun nextStory() {
        currentIndex++
        if (currentIndex >= stories.size) {
            currentIndex = stories.size - 1
        }
        applyCurrentStory()
    }

    fun prevStory() {
        currentIndex--
        if (0 > currentIndex) {
            currentIndex = 0
        }
        applyCurrentStory()
    }

    private fun applyCurrentStory() {
        _storyFlow.tryEmit(stories[currentIndex])
        startSendStoryTimer()
    }

    private fun startSendStoryTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            delay(5000)
            nextStory()
        }
    }

    fun addWallet(context: Context) = accountRepository.selectedWalletFlow.take(1)
        .filter { it.type != Wallet.Type.Watch && it.type != Wallet.Type.Ledger }
        .map { wallet ->
            accountRepository.addWallet(
                label = wallet.label,
                publicKey = wallet.publicKey,
                versions = listOf(WalletVersion.V5R1),
                type = wallet.type
            ).first()
        }.map { wallet ->
            if (wallet.hasPrivateKey && !passcodeManager.confirmation(context, context.getString(Localization.app_name))) {
                throw Exception("wrong passcode")
            }
            wallet
        }.onEach { wallet ->
            backupRepository.addBackup(wallet.id, BackupEntity.Source.LOCAL)
            accountRepository.setSelectedWallet(wallet.id)
        }.flowOn(Dispatchers.IO).take(1)

}