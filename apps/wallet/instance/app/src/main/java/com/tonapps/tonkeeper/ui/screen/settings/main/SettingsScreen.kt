package com.tonapps.tonkeeper.ui.screen.settings.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.extensions.toastLoading
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.manager.widget.WidgetManager
import com.tonapps.tonkeeper.popup.ActionSheet
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.backup.main.BackupScreen
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.ui.screen.settings.currency.CurrencyScreen
import com.tonapps.tonkeeper.ui.screen.settings.language.LanguageScreen
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameScreen
import com.tonapps.tonkeeper.ui.screen.notifications.NotificationsManageScreen
import com.tonapps.tonkeeper.ui.screen.settings.apps.AppsScreen
import com.tonapps.tonkeeper.ui.screen.settings.legal.LegalScreen
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Adapter
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Item
import com.tonapps.tonkeeper.ui.screen.settings.security.SecurityScreen
import com.tonapps.tonkeeper.ui.screen.settings.theme.ThemeScreen
import com.tonapps.tonkeeper.ui.screen.stories.w5.W5StoriesScreen
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.SearchEngine
import com.tonapps.wallet.localization.Localization
import uikit.base.BaseFragment
import uikit.dialog.alert.AlertDialog
import uikit.extensions.collectFlow
import uikit.widget.item.ItemTextView

class SettingsScreen(
    wallet: WalletEntity
): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    override val fragmentName: String = "SettingsScreen"

    private val from: String by lazy { requireArguments().getString(ARG_FROM)!! }

    override val viewModel: SettingsViewModel by walletViewModel()

    private val reviewManager: ReviewManager by lazy {
        ReviewManagerFactory.create(requireContext())
    }

    private val searchEngineMenu: ActionSheet by lazy {
        ActionSheet(requireContext())
    }

    private val adapter = Adapter(::onClickItem)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.simpleTrackScreenEvent("settings_open", viewModel.installId, from)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.settings))
        setAdapter(adapter)

        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    private fun onClickItem(item: Item) {
        AnalyticsHelper.simpleTrackEvent("settings_select", viewModel.installId, hashMapOf(
            "type" to item.name
        ))
        when (item) {
            is Item.Backup -> navigation?.add(BackupScreen.newInstance(screenContext.wallet))
            is Item.Currency -> navigation?.add(CurrencyScreen.newInstance())
            is Item.Language -> navigation?.add(LanguageScreen.newInstance())
            is Item.Account -> navigation?.add(EditNameScreen.newInstance(item.wallet))
            is Item.Theme -> navigation?.add(ThemeScreen.newInstance(screenContext.wallet))
            is Item.Widget -> installWidget()
            is Item.Security -> navigation?.add(SecurityScreen.newInstance(screenContext.wallet))
            is Item.Legal -> navigation?.add(LegalScreen.newInstance())
            is Item.News -> navigation?.openURL(item.url)
            is Item.Support -> navigation?.openURL(item.url)
            is Item.Contact -> navigation?.openURL(item.url)
            is Item.Tester -> navigation?.openURL(item.url)
            is Item.W5 -> navigation?.add(W5StoriesScreen.newInstance(!screenContext.wallet.isW5))
            is Item.Battery -> navigation?.add(BatteryScreen.newInstance(screenContext.wallet, from = "settings"))
            is Item.Logout -> if (item.delete) deleteAccount() else showSignOutDialog()
            is Item.ConnectedApps -> navigation?.add(AppsScreen.newInstance(screenContext.wallet))
            is Item.SearchEngine -> searchPicker(item)
            is Item.DeleteWatchAccount -> deleteAccount()
            is Item.Rate -> openRate()
            is Item.V4R2 -> viewModel.createV4R2Wallet()
            is Item.Notifications -> navigation?.add(NotificationsManageScreen.newInstance(screenContext.wallet))
            is Item.FAQ -> navigation?.openURL(item.url)
            else -> return
        }
    }

    private fun openRate() {
        activity?.let {
            reviewManager.requestReviewFlow().addOnCompleteListener(it) { task ->
                if (task.isSuccessful) {
                    startReviewFlow(task.result)
                } else {
                    openGooglePlay()
                }
            }
        }
    }

    private fun startReviewFlow(reviewInfo: ReviewInfo) {
        activity?.let {
            reviewManager.launchReviewFlow(it, reviewInfo).addOnCompleteListener(it) { task ->
                if (!task.isSuccessful) {
                    openGooglePlay()
                }
            }
        }
    }

    private fun openGooglePlay() {
        context?.let {
            val packageName = it.packageName.replace(".debug", "")
            val uri = "market://details?id=$packageName"
            val intent = Intent(Intent.ACTION_VIEW, uri.toUri())
            if (intent.resolveActivity(it.packageManager) != null) {
                startActivity(intent)
            }
        }
    }

    private fun searchPicker(item: Item.SearchEngine) {
        if (searchEngineMenu.isShowing) {
            searchEngineMenu.dismiss()
            return
        }

        val index = adapter.currentList.indexOf(item)
        val itemView = findListItemView(index) as? ItemTextView ?: return

        searchEngineMenu.clearItems()
        for (searchEngine in SearchEngine.all) {
            val checkedIcon = if (searchEngine.title.equals(item.value, ignoreCase = true)) {
                getDrawable(UIKitIcon.ic_done_16)
            } else {
                null
            }
            searchEngineMenu.addItem(searchEngine.id, searchEngine.title, icon = checkedIcon)
        }
        searchEngineMenu.doOnItemClick = {
            viewModel.setSearchEngine(SearchEngine.byId(it.id))
        }
        searchEngineMenu.show(itemView.dataView)
    }

    private fun installWidget() {
        WidgetManager.installBalance(requireActivity(), screenContext.wallet.id)
    }

    private fun showSignOutDialog() {
        val dialog = SignOutDialog(requireContext(), screenContext.wallet)
        dialog.show { signOut() }
    }

    private fun deleteAccount() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setMessage(Localization.delete_account_alert)
        builder.setNegativeButton(Localization.delete) { signOut() }
        builder.setPositiveButton(Localization.cancel)
        builder.show()
    }

    private fun signOut() {
        navigation?.toastLoading(true)
        viewModel.signOut {
            navigation?.toastLoading(false)
            finish()
        }
    }

    companion object {

        private const val ARG_FROM = "from"

        fun newInstance(wallet: WalletEntity, from: String): SettingsScreen {
            val screen = SettingsScreen(wallet)
            screen.putStringArg(ARG_FROM, from)
            return screen
        }
    }
}