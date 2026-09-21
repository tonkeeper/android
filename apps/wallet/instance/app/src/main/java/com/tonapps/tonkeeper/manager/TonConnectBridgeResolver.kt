package com.tonapps.tonkeeper.manager

import android.content.Context
import com.tonapps.log.L
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.manager.tonconnect.ITonConnectBridge
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.walletkit.WalletKitTonConnect
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import kotlinx.coroutines.CoroutineScope

class TonConnectBridgeResolver constructor(
    private var api: API,
    private val manager: TonConnectManager,
    private val pushManager: PushManager,
    private val context: Context,
    private val scope: CoroutineScope,
    private val accountRepository: UnifiedAccountRepository,
    private val dAppsRepository: DAppsRepository,
    private val wrapped: ITonConnectBridge
) : ITonConnectBridge by wrapped {

    companion object {
        fun create(
            api: API,
            manager: TonConnectManager,
            pushManager: PushManager,
            context: Context,
            scope: CoroutineScope,
            accountRepository: UnifiedAccountRepository,
            dAppsRepository: DAppsRepository,
            chainKitProvider: ChainKitProvider,
        ): TonConnectBridgeResolver {
            val isWalletKitEnabled = WalletKitTonConnect.isEnabled(api)
            L.e("isWalletKitEnabled: ${isWalletKitEnabled}")
            val wrapped: ITonConnectBridge = if (isWalletKitEnabled) {
                WalletKitTonConnect(
                    context = context,
                    scope = scope,
                    accountRepository = accountRepository,
                    dAppsRepository = dAppsRepository,
                    pushManager = pushManager,
                    chainKitProvider = chainKitProvider,
                    manager = manager
                )
            } else {
                manager
            }

            return TonConnectBridgeResolver(
                api, manager, pushManager, context, scope, accountRepository, dAppsRepository, wrapped
            )
        }
    }
}