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
import com.tonapps.tonkeeper.koin.passcodeManager
import com.tonapps.tonkeeper.koin.rnLegacy
import com.tonapps.tonkeeper.koin.settingsRepository
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.stateList
import kotlinx.coroutines.launch
import uikit.extensions.activity
import uikit.extensions.drawable
import uikit.extensions.withAlpha
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
            if (byUser && item.isPush) {
                togglePush(item.walletId, checked)
            } else if (byUser) {
                toggleBiometric(checked)
            }
        }
        itemView.background = item.position.drawable(context)
        iconView.setImageResource(item.iconRes)
        textView.setText(item.textRes)
        switchView.setChecked(item.enabled, false)
    }

    private fun togglePush(walletId: String, enable: Boolean) {
        if (!enable) {
            settingsRepository?.setPushWallet(walletId, false)
            return
        }

        if (context.hasPushPermission()) {
            settingsRepository?.setPushWallet(walletId, true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val activity = context.activity ?: return
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
                switchView.setChecked(!value, false)
            }
        }
    }

}