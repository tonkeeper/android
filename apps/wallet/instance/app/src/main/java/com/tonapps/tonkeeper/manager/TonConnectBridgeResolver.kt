package com.tonapps.tonkeeper.manager

import android.content.Context
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.manager.tonconnect.ITonConnectBridge
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.walletkit.WalletKitTonConnect
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.dapps.DAppsRepository
import kotlinx.coroutines.CoroutineScope

class TonConnectBridgeResolver constructor(
    private var api: API,
    private val manager: TonConnectManager,
    private val pushManager: PushManager,
    private val context: Context,
    private val scope: CoroutineScope,
    private val accountRepository: AccountRepository,
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
            accountRepository: AccountRepository,
            dAppsRepository: DAppsRepository
        ): TonConnectBridgeResolver {
            val wrapped: ITonConnectBridge = if (WalletKitTonConnect.isEnabled(api)) {
                WalletKitTonConnect(
                    context = context,
                    scope = scope,
                    accountRepository = accountRepository,
                    dAppsRepository = dAppsRepository,
                    pushManager = pushManager,
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