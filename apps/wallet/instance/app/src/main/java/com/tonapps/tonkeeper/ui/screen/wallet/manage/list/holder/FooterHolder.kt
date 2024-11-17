package com.tonapps.tonkeeper.ui.screen.wallet.manage.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.settings.security.SecurityScreen
import com.tonapps.tonkeeper.ui.screen.wallet.manage.TokensManageScreen
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.extensions.getSpannable
import uikit.navigation.Navigation

class FooterHolder(parent: ViewGroup): Holder<Item.SafeMode>(parent, R.layout.view_item_safemode) {

    private val view = findViewById<AppCompatTextView>(R.id.view)
    private val navigation: Navigation?
        get() = Navigation.from(context)

    init {
        view.text = context.getSpannable(Localization.safe_mode_tokens_footer)
        view.setOnClickListener { openSecurityScreen() }
    }

    private fun openSecurityScreen() {
        val nav = navigation ?: return
        nav.removeByClass({
            nav.add(SecurityScreen.newInstance())
        }, TokensManageScreen::class.java)
    }

    override fun onBind(item: Item.SafeMode) {

    }

}