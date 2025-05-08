package com.tonapps.tonkeeper.ui.screen.qr

import android.os.Bundle
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.koin.settingsRepository
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.coroutines.launch
import uikit.base.BaseFragment
import uikit.dialog.modal.ModalDialog
import uikit.widget.FrescoView
import uikit.widget.HeaderView

class EnableTronDialog(
    fragment: BaseFragment,
    private val wallet: WalletEntity,
    private val onEnable: suspend () -> Unit
) :
    ModalDialog(fragment.requireContext(), R.layout.dialog_enable_tron) {

    private lateinit var headerView: HeaderView
    private lateinit var iconView: FrescoView
    private lateinit var networkIconView: FrescoView
    private lateinit var buttonView: Button
    private lateinit var laterButtonView: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        headerView = findViewById(R.id.header)!!
        iconView = findViewById(R.id.icon)!!
        networkIconView = findViewById(R.id.network_icon)!!
        buttonView = findViewById(R.id.button)!!
        laterButtonView = findViewById(R.id.later)!!

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
    }
}