package com.tonkeeper.fragment.settings.legal

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.R
import com.tonkeeper.fragment.settings.list.SettingsAdapter
import com.tonkeeper.fragment.settings.list.item.SettingsIconItem
import com.tonkeeper.fragment.settings.list.item.SettingsIdItem
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonkeeper.fragment.settings.list.item.SettingsTitleItem
import uikit.base.fragment.BaseFragment
import uikit.list.ListCell
import uikit.navigation.Navigation.Companion.nav
import uikit.widget.HeaderView

class LegalFragment: BaseFragment(R.layout.fragment_legal), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = LegalFragment()
    }

    override var doOnDragging: ((Boolean) -> Unit)? = null
    override var doOnDraggingProgress: ((Float) -> Unit)? = null

    private val items = mutableListOf<SettingsItem>()

    init {
        items.add(SettingsIconItem(
                id = SettingsIdItem.TERMS_ID,
                titleRes = R.string.terms,
                position = ListCell.Position.FIRST
        ))

        items.add(SettingsIconItem(
            id = SettingsIdItem.PRIVACY_ID,
            titleRes = R.string.privacy,
            position = ListCell.Position.LAST
        ))

        items.add(SettingsTitleItem(
            titleRes = R.string.licenses
        ))

        items.add(SettingsIconItem(
            id = SettingsIdItem.LICENSES_FONT_ID,
            titleRes = R.string.montserrat_font,
            position = ListCell.Position.SINGLE
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
            nav()?.openURL("https://tonkeeper.com/terms/")
        }

        privacyView = view.findViewById(R.id.privacy)
        privacyView.setOnClickListener {
            nav()?.openURL("https://tonkeeper.com/privacy/")
        }
        licensesFontView = view.findViewById(R.id.licenses_font)
    }

}