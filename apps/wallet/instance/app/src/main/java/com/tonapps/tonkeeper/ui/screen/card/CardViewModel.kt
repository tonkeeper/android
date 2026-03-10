package com.tonapps.tonkeeper.ui.screen.card

import android.app.Application
import android.net.Uri
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.filterList
import com.tonapps.extensions.locale
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.tonconnect.bridge.JsonBuilder
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.InjectedTonConnectScreen
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class CardViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val tonConnectManager: TonConnectManager,
    private val settingsRepository: SettingsRepository,
): InjectedTonConnectScreen.ViewModel(app, wallet, tonConnectManager) {

    override val url: Uri by lazy {
        val builder = Uri.parse("https://next.holders.io").buildUpon()
        builder.appendQueryParameter("lang", context.locale.language)
        builder.appendQueryParameter("currency", settingsRepository.currency.code)
        builder.appendQueryParameter("theme", "holders")
        builder.appendQueryParameter("theme-style", if (settingsRepository.theme.light) "light" else "dark")
        builder.appendQueryParameter("utm_source", "tonkeeper")
        builder.build()
    }


}