package com.tonapps.tonkeeper.ui.screen.qr

import android.os.Bundle
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.koin.serverConfig
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import uikit.base.BaseFragment
import uikit.dialog.modal.ModalDialog
import uikit.widget.AsyncImageView
import uikit.widget.HeaderView
import uikit.widget.TextHeaderView

class EnableTronDialog(
    fragment: BaseFragment,
    private val wallet: WalletEntity,
    private val onEnable: suspend () -> Unit
) :
    ModalDialog(fragment.requireContext(), R.layout.dialog_enable_tron) {

    private lateinit var headerView: HeaderView
    private lateinit var iconView: AsyncImageView
    private lateinit var networkIconView: AsyncImageView
    private lateinit var buttonView: Button
    private lateinit var laterButtonView: Button
    private lateinit var textHeaderView: TextHeaderView


    val isBatteryDisabled: Boolean
        get() = context.serverConfig?.flags?.disableBattery == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        headerView = findViewById(R.id.header)!!
        iconView = findViewById(R.id.icon)!!
        networkIconView = findViewById(R.id.network_icon)!!
        buttonView = findViewById(R.id.button)!!
        laterButtonView = findViewById(R.id.later)!!
        textHeaderView = findViewById(R.id.text_header)!!

        headerView.doOnActionClick = { dismiss() }
        iconView.setImageURI(TokenEntity.USDT_ICON_URI)
        networkIconView.setLocalRes(R.drawable.ic_tron)
        buttonView.setOnClickListener {
            lifecycleScope.launch {
                onEnable()
                dismiss()
            }
        }
        laterButtonView.setOnClickListener { dismiss() }

        textHeaderView.desciption = if (isBatteryDisabled) {
            context.getString(Localization.tron_toggle_trc_text)
        } else {
            context.getString(Localization.tron_toggle_text)
        }
    }
}