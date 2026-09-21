package com.tonapps.migration

import android.os.Bundle
import android.view.View
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.bus.generated.Events.Migration.MigrationFrom
import com.tonapps.core.ComposableFragment
import com.tonapps.core.navigation.NavigationDelegate
import com.tonapps.deposit.DepositFragment
import com.tonapps.migration.analytics.MigrationAnalytics
import com.tonapps.migration.data.MigrationFeeShortage
import com.tonapps.migration.screens.confirm.MigrationFeeOptionIcon
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.navigation.Navigation

class MigrationFragment : ComposableFragment(), BaseFragment.BottomSheet {

    override val fragmentName: String = "MigrationFragment"

    private val from: MigrationFrom by lazy {
        MigrationFrom.entries.firstOrNull { it.name == arguments?.getString(ARG_FROM) }
            ?: MigrationFrom.Deeplink
    }

    private val onboarding: Boolean by lazy {
        arguments?.getBoolean(ARG_ONBOARDING) == true
    }

    private var openHistoryOnFinish = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MigrationAnalytics.start(from)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? NavigationDelegate
        setContent {
            MigrationRouter(
                onClose = { finish() },
                onMigrationSucceeded = { openHistoryOnFinish = !onboarding },
                onAddWallet = {
                    finish()
                    delegate?.onOpenAddWallet()
                },
                onDepositForFees = ::openDepositForFees,
                onDepositForFeeOption = ::openDepositForFeeOption,
                skippable = onboarding,
            )
        }
    }

    override fun finishInternal() {
        if (openHistoryOnFinish) {
            openHistoryOnFinish = false
            val delegate = context?.activity as? NavigationDelegate
            delegate?.onOpenHistory()
        }
        super.finishInternal()
    }

    private fun openDepositForFees(wallet: WalletEntity, shortage: MigrationFeeShortage) {
        if (shortage is MigrationFeeShortage.Battery) {
            openBatteryForFees(wallet)
            return
        }
        openDepositForWallet(wallet)
    }

    private fun openDepositForFeeOption(wallet: WalletEntity, option: MigrationFeeOptionIcon) {
        if (option == MigrationFeeOptionIcon.Battery) {
            openBatteryForFees(wallet)
            return
        }
        openDepositForWallet(wallet)
    }

    private fun openBatteryForFees(wallet: WalletEntity) {
        val delegate = context?.activity as? NavigationDelegate
        delegate?.onOpenBattery(
            walletId = wallet.id,
            from = BatteryNativeFrom.InsufficientFunds,
        )
    }

    private fun openDepositForWallet(wallet: WalletEntity) {
        val navigation = Navigation.from(requireContext()) ?: return
        navigation.add(
            DepositFragment.create(
                walletId = wallet.id,
            ),
        )
    }

    companion object {

        private const val ARG_FROM = "from"
        private const val ARG_ONBOARDING = "onboarding"

        fun newInstance(
            from: MigrationFrom,
            onboarding: Boolean = false,
        ): MigrationFragment {
            val fragment = MigrationFragment()
            fragment.putStringArg(ARG_FROM, from.name)
            fragment.putBooleanArg(ARG_ONBOARDING, onboarding)
            return fragment
        }
    }
}
