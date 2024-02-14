package com.tonapps.tonkeeper.fragment.settings.legal

import android.os.Bundle
import android.view.View
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsIconItem
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsIdItem
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsTitleItem
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView

class LegalFragment: BaseFragment(R.layout.fragment_legal), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = LegalFragment()
    }

    private val items = mutableListOf<SettingsItem>()

    init {
        items.add(SettingsIconItem(
                id = SettingsIdItem.TERMS_ID,
                titleRes = Localization.terms,
                position = com.tonapps.uikit.list.ListCell.Position.FIRST
        ))

        items.add(SettingsIconItem(
            id = SettingsIdItem.PRIVACY_ID,
            titleRes = Localization.privacy,
            position = com.tonapps.uikit.list.ListCell.Position.LAST
        ))

        items.add(SettingsTitleItem(
            titleRes = Localization.licenses
        ))

        items.add(SettingsIconItem(
            id = SettingsIdItem.LICENSES_FONT_ID,
            titleRes = Localization.montserrat_font,
            position = com.tonapps.uikit.list.ListCell.Position.SINGLE
        ))
    }

    private lateinit var headerView: HeaderView
    private lateinit var termsView: View
    private lateinit var privacyView: View
    private lateinit var licensesFontView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        termsView = view.findViewById(R.id.terms)
        termsView.setOnClickListener {
            navigation?.openURL("https://tonkeeper.com/terms/")
        }

        privacyView = view.findViewById(R.id.privacy)
        privacyView.setOnClickListener {
            navigation?.openURL("https://tonkeeper.com/privacy/")
        }
        licensesFontView = view.findViewById(R.id.licenses_font)
    }

}