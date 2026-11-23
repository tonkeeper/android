package com.tonapps.wallet.features.ledger.progress

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.controller.LedgerPurpose
import kotlinx.coroutines.launch
import uikit.navigation.NavigationActivity
import java.lang.ref.WeakReference

class LedgerProgressPresenter(private val controller: LedgerController) {

    private var overlay: WeakReference<LedgerProgressFragment>? = null
    private var host: WeakReference<NavigationActivity>? = null

    private val LedgerProgressFragment.isAlive: Boolean
        get() = !isClosing && !isRemoving && lifecycle.currentState != Lifecycle.State.DESTROYED

    fun attach(activity: NavigationActivity) {
        host = WeakReference(activity)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                controller.closeIdleSession()
            }
        })
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.state.collect { showIfNeeded(activity) }
            }
        }
    }

    fun restoreAfterExternalRemoval() {
        val activity = host?.get() ?: return
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        activity.window.decorView.post {
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                showIfNeeded(activity)
            }
        }
    }

    private fun showIfNeeded(activity: NavigationActivity) {
        if (needsOverlay(controller.state.value) && !hasOverlay(activity)) {
            val fragment = LedgerProgressFragment.newInstance()
            overlay = WeakReference(fragment)
            activity.add(fragment)
        }
    }

    private fun hasOverlay(activity: NavigationActivity): Boolean {
        val tracked = overlay?.get()
        if (tracked != null && tracked.isAlive) {
            return true
        }
        return activity.supportFragmentManager.fragments.any { it is LedgerProgressFragment && it.isAlive }
    }

    companion object {

        private val inlinePurposes = setOf(LedgerPurpose.Connect, LedgerPurpose.ImportAccounts)

        fun needsOverlay(state: LedgerOperationState): Boolean = when (state) {
            LedgerOperationState.Idle -> false
            is LedgerOperationState.Active -> state.request.purpose !in inlinePurposes
            is LedgerOperationState.Failed -> state.request.purpose !in inlinePurposes
            is LedgerOperationState.Done -> state.request.purpose !in inlinePurposes
        }
    }
}
