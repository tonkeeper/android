package com.tonapps.wc

import android.app.Application
import androidx.annotation.WorkerThread
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import com.tonapps.log.L
import com.tonapps.wc.models.WcWalletConfig
import java.util.concurrent.atomic.AtomicBoolean

object WcInitializer {

    private val initAttempted = AtomicBoolean(false)

    @Volatile
    var isInitialized: Boolean = false
        private set

    @WorkerThread
    fun tryToInit(
        application: Application,
        config: WcWalletConfig,
        onError: ((Throwable) -> Unit)? = null,
    ) {
        if (!initAttempted.compareAndSet(false, true)) {
            return
        }

        try {
            System.loadLibrary("sqlcipher")
        } catch (t: Throwable) {
            L.e("WalletConnect", t)
            onError?.invoke(t)
            return
        }

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
                onSuccess = { isInitialized = true },
                onError = { error -> L.e("WalletConnect", error.throwable) },
            )
        } catch (t: Throwable) {
            L.e("WalletConnect", t)
            onError?.invoke(t)
        }
    }
}
