package com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder

import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.ExploreItem
import com.tonapps.tonkeeper.ui.screen.root.RootActivity
import com.tonapps.tonkeeperx.R
import uikit.extensions.activity
import uikit.widget.FrescoView

class ExploreAdsHolder(parent: ViewGroup): ExploreHolder<ExploreItem.Ads>(parent, R.layout.view_browser_ads) {

    private val activity: RootActivity?
        get() = context.activity as? RootActivity

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val descriptionView = findViewById<AppCompatTextView>(R.id.description)
    private val actionButton = findViewById<Button>(R.id.action)

    override fun onBind(item: ExploreItem.Ads) {
        iconView.setImageURI(item.app.icon, this)
        titleView.text = item.app.name
        descriptionView.text = item.app.description
        actionButton.text = item.button.title

        actionButton.setOnClickListener {
            activity?.processDeepLink(item.uri, true, context.packageName)
        }
    }

}