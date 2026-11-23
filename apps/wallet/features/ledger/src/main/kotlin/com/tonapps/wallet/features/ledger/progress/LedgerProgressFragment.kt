package com.tonapps.wallet.features.ledger.progress

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tonapps.core.ComposableFragment
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.wallet.data.ledger.connection.LedgerConnectionInteractor
import com.tonapps.wallet.features.ledger.pairing.LedgerPairingFragment
import uikit.navigation.Navigation.Companion.navigation
import com.tonapps.wallet.features.ledger.common.LedgerSystemActions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import uikit.base.BaseFragment

class LedgerProgressFragment : ComposableFragment(), BaseFragment.Modal {

    override val fragmentName: String = "LedgerProgressFragment"

    private val controller: LedgerController by inject()
    private val presenter: LedgerProgressPresenter by inject()
    private val connection: LedgerConnectionInteractor by inject()

    var isClosing: Boolean = false
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null && !LedgerProgressPresenter.needsOverlay(controller.state.value)) {
            isClosing = true
            super.finishInternal()
            return
        }
        val initialView = LedgerProgressView(controller.state.value).next(controller.state.value)
        val linkKinds = controller.state
            .mapNotNull { it.request?.deviceId }
            .distinctUntilChanged()
            .flatMapLatest(connection::linkKind)
        val views = controller.state
            .filter { it !is LedgerOperationState.Idle }
            .runningFold(initialView) { view, state -> view.next(state) }
            .combine(linkKinds) { view, kind -> view.copy(linkKind = kind) }
            .stateIn(viewLifecycleOwner.lifecycleScope, SharingStarted.WhileSubscribed(), initialView)
        setContent {
            val view by views.collectAsState()
            LedgerProgressSheet(
                view = view,
                onClose = ::closeByUser,
                onRetry = controller::retry,
                onSystemAction = { action -> LedgerSystemActions.perform(requireContext(), action) },
                onPairAgain = ::pairAgain,
            )
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    controller.state.collect { state ->
                        if (!LedgerProgressPresenter.needsOverlay(state)) {
                            close()
                        }
                    }
                }
                launch {
                    var previous: LedgerProgressView? = null
                    views.collect { current ->
                        if (previous?.let(current::pluggedInAfterConnectFailure) == true) {
                            controller.retry()
                        }
                        previous = current
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        val host = activity
        val removedExternally = !isClosing && host != null && !host.isChangingConfigurations && !host.isFinishing
        super.onDestroy()
        if (removedExternally) {
            presenter.restoreAfterExternalRemoval()
        }
    }

    override fun onBackPressed(): Boolean {
        closeByUser()
        return false
    }

    override fun finishInternal() {
        if (!isClosing) {
            isClosing = true
            releaseOperation()
        }
        super.finishInternal()
    }

    private fun pairAgain(walletId: String) {
        val host = navigation
        closeByUser()
        host?.add(LedgerPairingFragment.newRepairInstance(walletId))
    }

    private fun closeByUser() {
        releaseOperation()
        close()
    }

    private fun releaseOperation() {
        when (controller.state.value) {
            is LedgerOperationState.Active -> controller.cancel()
            is LedgerOperationState.Failed,
            is LedgerOperationState.Done -> controller.dismiss()
            LedgerOperationState.Idle -> Unit
        }
    }

    private fun close() {
        if (isClosing) {
            return
        }
        isClosing = true
        if (view?.isLaidOut == true) {
            finish()
        } else {
            super.finishInternal()
        }
    }

    companion object {

        fun newInstance(): LedgerProgressFragment = LedgerProgressFragment()
    }
}
