package com.tonkeeper.fragment.settings.security

import android.os.Bundle
import android.util.Log
import android.view.View
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.fragment.passcode.lock.LockScreen
import com.tonkeeper.fragment.wallet.phrase.PhraseWalletFragment
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.item.ItemSwitchView

class SecurityFragment : BaseFragment(R.layout.fragment_security), BaseFragment.SwipeBack {

    companion object {
        private const val RECORD_PHRASE_REQUEST = "record_phrase"

        fun newInstance() = SecurityFragment()
    }

    override var doOnDragging: ((Boolean) -> Unit)? = null
    override var doOnDraggingProgress: ((Float) -> Unit)? = null

    private lateinit var headerView: HeaderView
    private lateinit var biometricView: ItemSwitchView
    private lateinit var lockScreenView: ItemSwitchView
    private lateinit var recoveryPhraseView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigation?.setFragmentResultListener(RECORD_PHRASE_REQUEST) { _, _ ->
            navigation?.add(PhraseWalletFragment.newInstance())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        biometricView = view.findViewById(R.id.biometric)
        biometricView.checked = App.settings.biometric
        biometricView.doOnCheckedChanged = { App.settings.biometric = it }

        lockScreenView = view.findViewById(R.id.lock_screen)
        lockScreenView.checked = App.settings.lockScreen
        lockScreenView.doOnCheckedChanged = { App.settings.lockScreen = it }

        recoveryPhraseView = view.findViewById(R.id.recovery_phrase)
        recoveryPhraseView.setOnClickListener {
            openRecoveryPhrase()
        }
    }

    private fun openRecoveryPhrase() {
        navigation?.add(LockScreen.newInstance(RECORD_PHRASE_REQUEST))
    }
}