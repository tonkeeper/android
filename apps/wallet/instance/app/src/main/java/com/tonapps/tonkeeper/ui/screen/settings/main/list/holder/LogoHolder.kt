package com.tonapps.tonkeeper.ui.screen.settings.main.list.holder

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.tonapps.extensions.appVersionCode
import com.tonapps.extensions.appVersionName
import com.tonapps.tonkeeper.ui.screen.dev.DevScreen
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.iconPrimaryColor
import com.tonapps.wallet.localization.Localization
import uikit.navigation.Navigation

class LogoHolder(
    parent: ViewGroup,
    onClick: ((Item) -> Unit)
): Holder<Item.Logo>(parent, R.layout.view_settings_logo, onClick) {

    private val logoView = findViewById<LottieAnimationView>(R.id.logo)
    private val versionView = findViewById<AppCompatTextView>(R.id.version)

    init {
        itemView.setOnClickListener {
            Navigation.from(context)?.add(DevScreen.newInstance())
        }
        val color = context.iconPrimaryColor
        logoView.addValueCallback(KeyPath("**"), LottieProperty.COLOR_FILTER) {
            PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
    }

    override fun onBind(item: Item.Logo) {
        val builder = StringBuilder()
        builder.append(context.getString(Localization.version, context.appVersionName, context.appVersionCode))
        builder.append("\n")
        builder.append(item.installerSource.title)

        versionView.text = builder
    }


}
