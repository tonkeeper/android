package com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.Item
import com.tonapps.tonkeeper.ui.screen.settings.security.SecurityScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.extensions.getSpannable
import uikit.navigation.Navigation

class FooterHolder(parent: ViewGroup): Holder<Item.SafeMode>(parent, R.layout.view_item_safemode) {

    private val view = findViewById<AppCompatTextView>(R.id.view)

    init {
        view.text = context.getSpannable(Localization.safe_mode_tokens_footer)
        view.setOnClickListener {
            Navigation.from(context)?.add(SecurityScreen.newInstance())
        }
    }

    override fun onBind(item: Item.SafeMode) {

    }


}