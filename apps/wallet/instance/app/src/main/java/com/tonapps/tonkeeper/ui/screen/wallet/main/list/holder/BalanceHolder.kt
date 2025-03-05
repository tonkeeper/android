package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.setPadding
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeper.core.BalanceType
import com.tonapps.tonkeeper.extensions.badgeGreen
import com.tonapps.tonkeeper.extensions.badgeOrange
import com.tonapps.tonkeeper.extensions.badgePurple
import com.tonapps.tonkeeper.extensions.copyWithToast
import com.tonapps.tonkeeper.koin.settingsRepository
import com.tonapps.tonkeeper.ui.screen.backup.main.BackupScreen
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeper.view.BatteryView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.UIKitColor
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.accentPurpleColor
import com.tonapps.uikit.color.accentRedColor
import com.tonapps.uikit.color.iconSecondaryColor
import com.tonapps.uikit.color.resolveColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import uikit.HapticHelper
import uikit.base.BaseDrawable
import uikit.extensions.dp
import uikit.extensions.expandTouchArea
import uikit.extensions.setPaddingHorizontal
import uikit.extensions.withAlpha
import uikit.navigation.Navigation
import uikit.widget.LoaderView

class BalanceHolder(
    parent: ViewGroup
) : Holder<Item.Balance>(parent, R.layout.view_wallet_data) {

    private val settingsRepository: SettingsRepository? by lazy {
        context.settingsRepository
    }

    private val balanceView = itemView.findViewById<AppCompatTextView>(R.id.wallet_balance)
    private val batteryView = itemView.findViewById<BatteryView>(R.id.wallet_battery)
    private val walletAddressView = itemView.findViewById<AppCompatTextView>(R.id.wallet_address)
    private val walletLoaderView = itemView.findViewById<LoaderView>(R.id.wallet_loader)
    private val backupIconContainerView = itemView.findViewById<View>(R.id.backup_icon_container)
    private val backupIconView = itemView.findViewById<AppCompatImageView>(R.id.backup_icon)

    init {
        balanceView.setOnClickListener {
            settingsRepository?.let {
                it.hiddenBalances = !it.hiddenBalances
            }
            HapticHelper.impactLight(context)
        }
        walletLoaderView.setColor(context.iconSecondaryColor)
        walletLoaderView.setTrackColor(context.iconSecondaryColor.withAlpha(.32f))
        batteryView.expandTouchArea(left = 0, top = 10.dp, right = 24.dp, bottom = 10.dp)
    }

    override fun onBind(item: Item.Balance) {
        batteryView.setOnClickListener {
            Navigation.from(context)?.add(BatteryScreen.newInstance(item.wallet, from = "wallet"))
        }
        backupIconContainerView.setOnClickListener {
            Navigation.from(context)?.add(BackupScreen.newInstance(item.wallet))
        }

        if (item.hiddenBalance) {
            balanceView.text = HIDDEN_BALANCE
            balanceView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            balanceView.background = HiddenBalanceDrawable(context)
            balanceView.setPaddingHorizontal(12.dp)
            balanceView.translationY = 6f.dp
        } else {
            balanceView.text = item.balance.withCustomSymbol(context)
            balanceView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
            balanceView.background = null
            balanceView.setPadding(0)
            balanceView.translationY = 0f
        }

        val requestBackup =
            (item.walletType == Wallet.Type.Default || item.walletType == Wallet.Type.Lockup) && !item.hasBackup

        if (requestBackup && item.balanceType == BalanceType.Huge) {
            balanceView.setTextColor(context.accentRedColor)
            backupIconView.imageTintList = context.accentRedColor.stateList
            backupIconContainerView.visibility = View.VISIBLE
        } else if (requestBackup && item.balanceType == BalanceType.Positive) {
            balanceView.setTextColor(context.accentOrangeColor)
            backupIconView.imageTintList = context.accentOrangeColor.stateList
            backupIconContainerView.visibility = View.VISIBLE
        } else {
            balanceView.setTextColor(context.textPrimaryColor)
            backupIconContainerView.visibility = View.GONE
        }

        if (item.showBattery) {
            batteryView.visibility = View.VISIBLE
            batteryView.setBatteryLevel(item.batteryBalance.value.toFloat())
            batteryView.emptyState = item.batteryEmptyState
        } else {
            batteryView.visibility = View.GONE
        }

        setWalletState(
            state = item.status,
            wallet = item.wallet,
            lastUpdatedFormat = item.lastUpdatedFormat,
            showYourAddress = item.prefixYourAddress
        )
    }

    private fun setWalletState(
        state: Item.Status,
        wallet: WalletEntity,
        lastUpdatedFormat: String,
        showYourAddress: Boolean,
    ) {
        when (state) {
            Item.Status.LastUpdated -> {
                walletLoaderView.visibility = View.GONE
                walletAddressView.text = context.getString(Localization.last_updated, lastUpdatedFormat)
                walletAddressView.setTextColor(context.textSecondaryColor)
            }
            Item.Status.Updating -> {
                walletLoaderView.visibility = View.VISIBLE
                walletAddressView.setText(Localization.updating)
                walletAddressView.setTextColor(context.textSecondaryColor)
            }
            Item.Status.SendingTransaction -> {
                walletLoaderView.visibility = View.VISIBLE
                walletAddressView.setText(Localization.sending_transaction)
                walletAddressView.setTextColor(context.textSecondaryColor)
            }
            Item.Status.TransactionConfirmed -> {
                walletLoaderView.visibility = View.GONE
                walletAddressView.setText(Localization.transaction_confirmed)
                walletAddressView.setTextColor(context.accentGreenColor)
            }
            Item.Status.NoInternet -> {
                walletLoaderView.visibility = View.GONE
                walletAddressView.setText(Localization.no_internet_connection)
                walletAddressView.setTextColor(context.accentOrangeColor)
            }
            else -> {
                walletLoaderView.visibility = View.GONE
                setWalletAddressWithType(wallet.address.shortAddress, wallet.type, wallet.version, showYourAddress)
                walletAddressView.setTextColor(context.textSecondaryColor)
                walletAddressView.setOnClickListener {
                    val walletType = wallet.type
                    if (walletType == Wallet.Type.Testnet || walletType == Wallet.Type.Watch) {
                        context.copyWithToast(wallet.address, getTypeColor(walletType))
                    } else {
                        context.copyWithToast(wallet.address)
                    }
                    settingsRepository?.incrementCopyCount()
                }
            }
        }
    }

    private fun setWalletAddressWithType(
        address: String,
        type: Wallet.Type,
        version: WalletVersion,
        showYourAddress: Boolean,
    ) {
        var builder = SpannableStringBuilder()

        if (showYourAddress) {
            val prefix = if (type == Wallet.Type.Watch) getString(Localization.address_prefix) else getString(Localization.your_address)
            builder.append(prefix)
            builder.append(" ")
        }


        builder.append(address.shortAddress)

        if (version == WalletVersion.V5R1 || version == WalletVersion.V5BETA) {
            val resId = if (version == WalletVersion.V5BETA) {
                Localization.w5beta
            } else {
                Localization.w5
            }
            builder = builder.badgeGreen(context, resId)
        }

        builder = when (type) {
            Wallet.Type.Signer, Wallet.Type.SignerQR -> builder.badgePurple(context, Localization.signer)
            Wallet.Type.Ledger -> builder.badgeGreen(context, Localization.ledger)
            Wallet.Type.Testnet -> builder.badgeOrange(context, Localization.testnet)
            Wallet.Type.Watch -> builder.badgeOrange(context, Localization.watch_only)
            Wallet.Type.Keystone -> builder.badgePurple(context, Localization.keystone)
            Wallet.Type.Default -> builder
            Wallet.Type.Lockup -> builder
        }

        walletAddressView.text = builder
    }

    private fun getTypeColor(type: Wallet.Type): Int {
        return when (type) {
            Wallet.Type.Ledger -> context.accentGreenColor
            Wallet.Type.Signer, Wallet.Type.SignerQR -> context.accentPurpleColor
            else -> context.accentOrangeColor
        }
    }

    private class HiddenBalanceDrawable(context: Context) : BaseDrawable() {

        private val radius = 20f.dp

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resolveColor(UIKitColor.buttonSecondaryBackgroundColor)
        }

        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            rect.left = bounds.left.toFloat()
            rect.top = bounds.top.toFloat()
            rect.right = bounds.right.toFloat()
            rect.bottom = bounds.bottom.toFloat() - 12f.dp
        }

    }
}