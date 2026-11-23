package com.tonapps.wc

import android.app.Application
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.foundation.network.BaseRelayClient
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import com.tonapps.async.Async
import com.tonapps.log.L
import com.tonapps.wc.models.WcWalletConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object WcInitializer {

    internal enum class State {
        Initializing,
        Initialized,
        Failed,
    }

    private val initAttempted = AtomicBoolean(false)

    internal val state: StateFlow<State> field = MutableStateFlow(State.Initializing)

    val isInitialized: Boolean
        get() = state.value == State.Initialized

    private val onInitializedActions = mutableListOf<() -> Unit>()

    private var isWalletKitReady = false

    fun tryToInit(
        application: Application,
        config: WcWalletConfig,
        onError: ((Throwable) -> Unit)? = null,
    ) {
        if (!initAttempted.compareAndSet(false, true)) {
            return
        }

        Async.ioScope().launch {
            initialize(application, config, onError)
        }
    }

    internal suspend fun awaitInitialized(): Boolean {
        return state.first { it != State.Initializing } == State.Initialized
    }

    internal fun doOnInitialized(action: () -> Unit) {
        synchronized(onInitializedActions) {
            if (!isWalletKitReady) {
                onInitializedActions += action
                return
            }
        }
        action()
    }

    @WorkerThread
    private fun initialize(
        application: Application,
        config: WcWalletConfig,
        onError: ((Throwable) -> Unit)?,
    ) {
        if (!initializeWalletKit(application, config, onError)) {
            state.value = State.Failed
            return
        }

        val actions = synchronized(onInitializedActions) {
            isWalletKitReady = true
            onInitializedActions.toList().also { onInitializedActions.clear() }
        }
        actions.forEach { it() }
        state.value = State.Initialized

        if (hasRelaySubscriptions()) {
            Async.onMain {
                reconnectIfResumeMissed()
            }
        }
    }

    @WorkerThread
    private fun hasRelaySubscriptions(): Boolean {
        return WalletKit.getListOfActiveSessions().isNotEmpty() || CoreClient.Pairing.getPairings().isNotEmpty()
    }

    @MainThread
    private fun reconnectIfResumeMissed() {
        val isAppResumed = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val relayLifecycle = (CoreClient.Relay as? BaseRelayClient)?.connectionLifecycle ?: return
        if (isAppResumed && relayLifecycle.onResume.value == null) {
            relayLifecycle.reconnect()
        }
    }

    @WorkerThread
    private fun initializeWalletKit(
        application: Application,
        config: WcWalletConfig,
        onError: ((Throwable) -> Unit)?,
    ): Boolean {
        try {
            System.loadLibrary("sqlcipher")
        } catch (t: Throwable) {
            L.e("WalletConnect", t)
            onError?.invoke(t)
            return false
        }

        var isWalletKitInitialized = false
        try {
            CoreClient.initialize(
                metaData = Core.Model.AppMetaData(
                    name = config.appName,
                    description = config.appDescription,
                    url = config.appUrl,
                    icons = listOf(config.icon),
                    redirect = config.redirect,
                ),
                projectId = config.projectId,
                connectionType = ConnectionType.AUTOMATIC,
                application = application,
                telemetryEnabled = true,
                onError = { L.e(it.throwable) },
            )

            val initParams = Wallet.Params.Init(core = CoreClient)
            WalletKit.initialize(
                params = initParams,
                onSuccess = { isWalletKitInitialized = true },
                onError = { error -> L.e("WalletConnect", error.throwable) },
            )
        } catch (t: Throwable) {
            L.e("WalletConnect", t)
            onError?.invoke(t)
            return false
        }
        return isWalletKitInitialized
    }
}
