package com.tonapps.tonkeeper.ui.screen.w5.stories

import android.app.Application
import android.content.Context
import android.util.Log
import android.view.ViewConfiguration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.request.ImageRequest
import com.google.common.util.concurrent.AtomicDouble
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rn.RNLegacy
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class W5StoriesViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val passcodeManager: PasscodeManager,
    private val backupRepository: BackupRepository,
    private val rnLegacy: RNLegacy,
): BaseWalletVM(app) {

    val stories = StoryEntity.all
    private val autoSwitchDuration = 5000L
    private val progressDelay = 8L

    private val _storyFlow = MutableEffectFlow<StoryEntity>()
    val storyFlow = _storyFlow.asSharedFlow()

    private val progress = AtomicDouble(0.0)
    private val _progressFlow = MutableEffectFlow<Pair<Int, Float>>()
    val progressFlow = _progressFlow.asSharedFlow()

    private var isAutoSwitchPaused = false
    private var currentIndex = 0
    private var timerJob: Job? = null
    private var lastPauseTime = 0L

    private val isLastStory: Boolean
        get() = currentIndex == stories.size - 1

    private val isFirstStory: Boolean
        get() = currentIndex == 0

    init {
        applyCurrentStory()
    }

    fun pause() {
        stopStoryTimer()
        isAutoSwitchPaused = true
        lastPauseTime = System.currentTimeMillis()
    }

    fun resume(next: Boolean) {
        isAutoSwitchPaused = false
        val elapsedSincePause = System.currentTimeMillis() - lastPauseTime
        if (120 >= elapsedSincePause) {
            if (next && !isLastStory) {
                nextStory()
            } else if (!next && !isFirstStory) {
                prevStory()
            } else {
                startStoryTimer()
            }
        } else {
            startStoryTimer()
        }
    }

    fun nextStory() {
        if (isLastStory) {
            setProgress(1.0)
            return
        }
        currentIndex = (currentIndex + 1) % stories.size
        applyCurrentStory()
    }

    fun prevStory() {
        if (isFirstStory) {
            return
        }
        currentIndex = if (currentIndex - 1 < 0) stories.size - 1 else currentIndex - 1
        applyCurrentStory()
    }

    private fun applyCurrentStory() {
        _storyFlow.tryEmit(stories[currentIndex])
        progress.set(0.0)
        startStoryTimer()
    }

    private fun startStoryTimer() {
        stopStoryTimer()
        timerJob = viewModelScope.launch {
            val startFromProgress = progress.get()
            var remainingTime = autoSwitchDuration * (1 - startFromProgress)

            while (isActive && !isAutoSwitchPaused && remainingTime > 0) {
                delay(progressDelay)
                remainingTime -= progressDelay
                val progress = 1 - remainingTime / autoSwitchDuration
                setProgress(progress)
            }
            setProgress(1.0)
            nextStory()
        }
    }

    private fun setProgress(newProgress: Double) {
        progress.set(newProgress)
        _progressFlow.tryEmit(Pair(currentIndex, progress.toFloat()))
    }

    private fun stopStoryTimer() {
        timerJob?.cancel()
    }

    fun addWallet(context: Context) = accountRepository.selectedWalletFlow.take(1)
        .map { wallet ->
            val fixedLabel = wallet.label.name.replace(wallet.version.title, "") + " " + WalletVersion.V5R1.title
            accountRepository.addWallet(
                ids = listOf(AccountRepository.newWalletId()),
                label = Wallet.NewLabel(listOf(fixedLabel), wallet.label.emoji, wallet.label.color),
                publicKey = wallet.publicKey,
                versions = listOf(WalletVersion.V5R1),
                type = wallet.type
            ).first()
        }.map { wallet ->
            val mnemonic = accountRepository.getMnemonic(wallet.id) ?: throw Exception("mnemonic not found")
            val passcode = passcodeManager.requestValidPasscode(context)
            rnLegacy.addMnemonics(passcode, listOf(wallet.id), mnemonic.toList())
            wallet
        }.map { wallet ->
            backupRepository.addBackup(wallet.id, BackupEntity.Source.LOCAL)
            wallet.id
        }.flowOn(Dispatchers.IO)

}