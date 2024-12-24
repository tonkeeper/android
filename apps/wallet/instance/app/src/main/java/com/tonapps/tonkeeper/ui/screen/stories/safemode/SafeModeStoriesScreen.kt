package com.tonapps.tonkeeper.ui.screen.stories.safemode

import android.os.Bundle
import android.view.View
import com.facebook.common.util.UriUtil
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.widget.stories.BaseStoriesScreen

class SafeModeStoriesScreen: BaseStoriesScreen() {

    override val fragmentName: String = "SafeModeStoriesScreen"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val items = mutableListOf<Item>()
        items.add(Item(
            image = UriUtil.getUriForResourceId(R.drawable.safemode_stories_1),
            title = getString(Localization.stories_safemode_1_title),
            subtitle = getString(Localization.stories_safemode_1_subtitle)
        ))
        items.add(Item(
            image = UriUtil.getUriForResourceId(R.drawable.safemode_stories_2),
            title = getString(Localization.stories_safemode_2_title),
            subtitle = getString(Localization.stories_safemode_2_subtitle)
        ))
        items.add(Item(
            image = UriUtil.getUriForResourceId(R.drawable.safemode_stories_3),
            title = getString(Localization.stories_safemode_3_title),
            subtitle = getString(Localization.stories_safemode_3_subtitle)
        ))
        putItems(items)
    }

    companion object {
        fun newInstance() = SafeModeStoriesScreen()
    }
}
