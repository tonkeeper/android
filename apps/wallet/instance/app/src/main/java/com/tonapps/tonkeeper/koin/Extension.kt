package com.tonapps.tonkeeper.koin

import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.RemoteConfig
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.manager.apk.APKManager
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.base.compose.ComposeWalletScreen
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.definition.Definition
import org.koin.core.definition.KoinDefinition
import org.koin.core.module.Module
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.Qualifier

@MainThread
inline fun <reified T : ViewModel> BaseWalletScreen<ScreenContext.Wallet>.walletViewModel(
    qualifier: Qualifier? = null,
    noinline ownerProducer: () -> ViewModelStoreOwner = { this },
    noinline extrasProducer: (() -> CreationExtras)? = null,
    noinline parameters: ParametersDefinition = { parametersOf() },
): Lazy<T> {
    return lazy(LazyThreadSafetyMode.NONE) {
        getViewModel(
            qualifier = qualifier,
            ownerProducer = ownerProducer,
            extrasProducer = extrasProducer,
            parameters = {
                parameters.invoke().insert(0, screenContext.wallet)
            }
        )
    }
}

@MainThread
inline fun <reified T : ViewModel> ComposeWalletScreen.walletViewModel(
    qualifier: Qualifier? = null,
    noinline ownerProducer: () -> ViewModelStoreOwner = { this },
    noinline extrasProducer: (() -> CreationExtras)? = null,
    noinline parameters: ParametersDefinition = { parametersOf() },
): Lazy<T> {
    return lazy(LazyThreadSafetyMode.NONE) {
        getViewModel(
            qualifier = qualifier,
            ownerProducer = ownerProducer,
            extrasProducer = extrasProducer,
            parameters = {
                parameters.invoke().insert(0, wallet)
            }
        )
    }
}


val Context.koin: Koin?
    get() = (applicationContext as? KoinComponent)?.getKoin()

val Context.accountRepository: AccountRepository?
    get() = koin?.get()

val Context.api: API?
    get() = koin?.get<API>()

val Context.remoteConfig: RemoteConfig?
    get() = koin?.get<RemoteConfig>()

val Context.serverConfig: ConfigEntity?
    get() = api?.config

val Context.settingsRepository: SettingsRepository?
    get() = koin?.get<SettingsRepository>()

val Context.passcodeManager: PasscodeManager?
    get() = koin?.get<PasscodeManager>()

val Context.rnLegacy: RNLegacy?
    get() = koin?.get<RNLegacy>()

val Context.historyHelper: HistoryHelper?
    get() = koin?.get<HistoryHelper>()

val Context.pushManager: PushManager?
    get() = koin?.get<PushManager>()

val Context.apkManager: APKManager?
    get() = koin?.get<APKManager>()

val Context.installId: String
    get() = settingsRepository?.installId ?: ""