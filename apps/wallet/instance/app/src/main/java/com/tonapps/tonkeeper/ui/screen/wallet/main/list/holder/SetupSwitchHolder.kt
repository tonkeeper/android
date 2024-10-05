package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.Manifest
import android.os.Build
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.extensions.hasPushPermission
import com.tonapps.tonkeeper.extensions.showToast
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.koin.passcodeManager
import com.tonapps.tonkeeper.koin.pushManager
import com.tonapps.tonkeeper.koin.rnLegacy
import com.tonapps.tonkeeper.koin.settingsRepository
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeper.worker.PushToggleWorker
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.stateList
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import uikit.extensions.activity
import uikit.extensions.drawable
import uikit.extensions.withAlpha
import uikit.navigation.Navigation
import uikit.widget.SwitchView

class SetupSwitchHolder(parent: ViewGroup): Holder<Item.SetupSwitch>(parent, R.layout.view_wallet_setup_switch) {

    private val settingsRepository = context.settingsRepository
    private val passcodeManager = context.passcodeManager
    private val rnLegacy = context.rnLegacy

    private val iconView = findViewById<AppCompatImageView>(R.id.icon)
    private val textView = findViewById<AppCompatTextView>(R.id.text)
    private val switchView = findViewById<SwitchView>(R.id.enabled)

    init {
        val greenColor = context.accentGreenColor
        iconView.imageTintList = greenColor.stateList
        iconView.backgroundTintList = greenColor.withAlpha(.12f).stateList
        itemView.setOnClickListener { switchView.toggle(true) }
    }

    override fun onBind(item: Item.SetupSwitch) {
        switchView.doCheckedChanged = { checked, byUser ->
            if (byUser) {
                if (item.settingsType == Item.SetupSwitch.TYPE_PUSH) {
                    togglePush(item.wallet, checked)
                } else if (item.settingsType == Item.SetupSwitch.TYPE_BIOMETRIC) {
                    toggleBiometric(checked)
                }
            }
        }
        itemView.background = item.position.drawable(context)
        iconView.setImageResource(item.iconRes)
        textView.setText(item.textRes)
        switchView.setChecked(item.enabled, false)
    }

    private fun togglePush(wallet: WalletEntity, enable: Boolean) {
        if (!enable) {
            PushToggleWorker.run(context, wallet, PushManager.State.Disable)
            return
        }

        if (context.hasPushPermission()) {
            PushToggleWorker.run(context, wallet, PushManager.State.Enable)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val activity = context.activity ?: return
            switchView.setChecked(newChecked = false, byUser = false)
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }

    private fun toggleBiometric(value: Boolean) {
        lifecycleScope?.launch {
            try {
                if (value) {
                    val code = passcodeManager?.requestValidPasscode(context) ?: throw IllegalStateException()
                    rnLegacy?.setupBiometry(code)
                } else {
                    rnLegacy?.removeBiometry()
                }
                settingsRepository?.biometric = value
            } catch (e: Throwable) {
                context.showToast(Localization.biometric_enabled)
                switchView.setChecked(!value, false)
            }
        }
    }

}