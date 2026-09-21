package com.tonapps.tonkeeper.ui.screen.settings.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.Wallet as TonWallet
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.bus.generated.Events.InappReview.InappReviewAction
import com.tonapps.bus.generated.Events.Migration.MigrationFrom
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowSource
import com.tonapps.core.flags.InAppReviewManager
import com.tonapps.tonkeeper.Wallet
import com.tonapps.tonkeeper.extensions.toastLoading
import com.tonapps.tonkeeper.manager.widget.WidgetManager
import com.tonapps.tonkeeper.popup.ActionSheet
import com.tonapps.migration.MigrationFragment
import com.tonapps.dapp.screens.sessions.WcSessionsFragment
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.backup.main.BackupScreen
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.ui.screen.settings.currency.CurrencyScreen
import com.tonapps.tonkeeper.ui.screen.settings.language.LanguageScreen
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameScreen
import com.tonapps.tonkeeper.ui.screen.notifications.NotificationsManageScreen
import com.tonapps.tonkeeper.ui.screen.settings.apps.AppsScreen
import com.tonapps.tonkeeper.ui.screen.settings.extensions.ExtensionsScreen
import com.tonapps.tonkeeper.ui.screen.settings.legal.LegalScreen
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Adapter
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Item
import com.tonapps.tonkeeper.ui.screen.settings.security.SecurityScreen
import com.tonapps.tonkeeper.ui.screen.settings.theme.ThemeScreen
import com.tonapps.tonkeeper.ui.screen.stories.w5.W5StoriesScreen
import com.tonapps.tonkeeper.ui.screen.support.SupportScreen
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.core.SearchEngine
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.dialog.alert.AlertDialog
import uikit.extensions.collectFlow
import uikit.widget.item.ItemTextView

class SettingsScreen : BaseListWalletScreen<ScreenContext.None>(ScreenContext.None), BaseFragment.SwipeBack {

    override val fragmentName: String = "SettingsScreen"

    private val from: String by lazy { requireArguments().getString(ARG_FROM)!! }

    override val viewModel: SettingsViewModel by viewModel()

    private val reviewManager: ReviewManager by lazy {
        ReviewManagerFactory.create(requireContext())
    }

    private val searchEngineMenu: ActionSheet by lazy {
        ActionSheet(requireContext())
    }

    private val adapter = Adapter(::onClickItem)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics?.simpleTrackScreenEvent("settings_open", from)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.settings))
        setAdapter(adapter)

        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    private fun onClickItem(item: Item) {
        analytics?.simpleTrackEvent("settings_select", hashMapOf(
            "type" to item.name
        ))
        val wallet = navigationWalletForScreens() ?: return
        when (item) {
            is Item.Backup -> navigation?.add(BackupScreen.newInstance(WalletFlowSource.Settings))
            is Item.Currency -> navigation?.add(CurrencyScreen.newInstance())
            is Item.Language -> navigation?.add(LanguageScreen.newInstance())
            is Item.Account -> navigation?.add(EditNameScreen.newInstance())
            is Item.Theme -> navigation?.add(ThemeScreen.newInstance())
            is Item.Widget -> installWidget()
            is Item.Security -> navigation?.add(SecurityScreen.newInstance(wallet))
            is Item.Legal -> navigation?.add(LegalScreen.newInstance())
            is Item.News -> navigation?.openURL(item.url)
            is Item.Support -> navigation?.add(SupportScreen.newInstance())
            is Item.Tester -> navigation?.openURL(item.url)
            is Item.W5 -> {
                val legacy = (viewModel.walletFlow.value as? Wallet.Legacy)?.entity ?: return
                navigation?.add(W5StoriesScreen.newInstance(!legacy.isW5))
            }
            is Item.Battery -> navigation?.add(BatteryScreen.newInstance(wallet, from = BatteryNativeFrom.Settings))
            is Item.Logout -> if (item.delete) deleteAccount() else showSignOutDialog()
            is Item.ConnectedApps -> when (viewModel.walletFlow.value) {
                is Wallet.Multichain -> navigation?.add(WcSessionsFragment.newInstance())
                is Wallet.Legacy -> navigation?.add(AppsScreen.newInstance(wallet))
                null -> Unit
            }
            is Item.InstalledExtensions -> navigation?.add(ExtensionsScreen.newInstance(wallet))
            is Item.SearchEngine -> searchPicker(item)
            is Item.DeleteWatchAccount -> deleteAccount()
            is Item.Rate -> openRate()
            is Item.V4R2 -> viewModel.createV4R2Wallet()
            is Item.Notifications -> navigation?.add(NotificationsManageScreen.newInstance(wallet))
            is Item.FAQ -> navigation?.openURL(item.url)
            is Item.Migration -> {
                viewModel.markMigrationOpened()
                navigation?.add(MigrationFragment.newInstance(MigrationFrom.Settings))
            }
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
            AnalyticsHelper.Default.events.inappReview.inappReview(InappReviewAction.Manual)
            InAppReviewManager.onManualReviewRequested()
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

    private fun navigationWalletForScreens(): WalletEntity? {
        return when (val kind = viewModel.walletFlow.value ?: return null) {
            is Wallet.Legacy -> kind.entity
            is Wallet.Multichain -> {
                val mc = kind.entity
                WalletEntity.EMPTY.copy(
                    id = mc.id,
                    label = TonWallet.Label(mc.name, mc.emoji, mc.color),
                    version = WalletVersion.V4R2,
                    type = WalletType.Default,
                )
            }
        }
    }

    private fun installWidget() {
        val id = navigationWalletForScreens()?.id ?: return
        WidgetManager.installBalance(requireActivity(), id)
    }

    private fun showSignOutDialog() {
        val w = navigationWalletForScreens() ?: return
        val dialog = SignOutDialog(requireContext(), w)
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

        fun newInstance(from: String): SettingsScreen {
            val screen = SettingsScreen()
            screen.putStringArg(ARG_FROM, from)
            return screen
        }
    }
}
