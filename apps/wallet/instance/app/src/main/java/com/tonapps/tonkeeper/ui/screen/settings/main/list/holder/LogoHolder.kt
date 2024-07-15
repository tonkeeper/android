package com.tonapps.tonkeeper.ui.screen.settings.main.list.holder

import android.content.pm.PackageInfo
import android.os.Build
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.extensions.appVersionCode
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.packageInfo
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization

class LogoHolder(
    parent: ViewGroup,
    onClick: ((Item) -> Unit)
): Holder<Item.Logo>(parent, R.layout.view_settings_logo, onClick) {

    private val versionView = findViewById<AppCompatTextView>(R.id.version)

    override fun onBind(item: Item.Logo) {
        versionView.text = context.getString(Localization.version, context.appVersionName, context.appVersionCode)
    }


}