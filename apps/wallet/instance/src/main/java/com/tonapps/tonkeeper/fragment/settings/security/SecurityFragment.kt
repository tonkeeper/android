package com.tonapps.tonkeeper.fragment.settings.security

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.event.WalletSettingsEvent
import com.tonapps.tonkeeper.extensions.isRecoveryPhraseBackup
import com.tonapps.tonkeeper.fragment.passcode.lock.LockScreen
import com.tonapps.tonkeeper.fragment.wallet.phrase.PhraseWalletFragment
import core.EventBus
import kotlinx.coroutines.launch
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.item.ItemIconView
import uikit.widget.item.ItemSwitchView

class SecurityFragment : BaseFragment(R.layout.fragment_security), BaseFragment.SwipeBack {

    companion object {
        private const val RECORD_PHRASE_REQUEST = "record_phrase"

        fun newInstance() = SecurityFragment()
    }

    private val walletSettingsUpdate = fun(_: WalletSettingsEvent) {
        requestWallet()
    }

    private lateinit var headerView: HeaderView
    private lateinit var biometricView: ItemSwitchView
    private lateinit var lockScreenView: ItemSwitchView
    private lateinit var recoveryPhraseView: ItemIconView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigation?.setFragmentResultListener(RECORD_PHRASE_REQUEST) { _ ->
            navigation?.add(PhraseWalletFragment.newInstance())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        biometricView = view.findViewById(R.id.biometric)
        biometricView.checked = com.tonapps.tonkeeper.App.settings.biometric
        biometricView.doOnCheckedChanged = { com.tonapps.tonkeeper.App.settings.biometric = it }

        lockScreenView = view.findViewById(R.id.lock_screen)
        lockScreenView.checked = com.tonapps.tonkeeper.App.settings.lockScreen
        lockScreenView.doOnCheckedChanged = { com.tonapps.tonkeeper.App.settings.lockScreen = it }

        recoveryPhraseView = view.findViewById(R.id.recovery_phrase)
        recoveryPhraseView.setOnClickListener {
            openRecoveryPhrase()
        }

        requestWallet()

        EventBus.subscribe(WalletSettingsEvent::class.java, walletSettingsUpdate)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.unsubscribe(WalletSettingsEvent::class.java, walletSettingsUpdate)
    }

    private fun openRecoveryPhrase() {
        navigation?.add(LockScreen.newInstance(RECORD_PHRASE_REQUEST))
    }

    private fun requestWallet() {
        lifecycleScope.launch {
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@launch
            recoveryPhraseView.dot = !wallet.isRecoveryPhraseBackup()

            recoveryPhraseView.visibility = if (wallet.hasPrivateKey) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
}