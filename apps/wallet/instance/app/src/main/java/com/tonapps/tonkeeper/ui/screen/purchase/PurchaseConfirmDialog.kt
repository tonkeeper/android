package com.tonapps.tonkeeper.ui.screen.purchase

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.ui.screen.onramp.main.entities.ProviderEntity
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.wallet.data.purchase.entity.MerchantEntity
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import com.tonapps.wallet.localization.Localization
import uikit.dialog.modal.ModalDialog
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.clickable
import uikit.widget.AsyncImageView
import uikit.widget.CheckBoxView
import uikit.widget.HeaderView

class PurchaseConfirmDialog(
    context: Context
): ModalDialog(context, R.layout.fragment_purchase_confirm) {

    private val headerView: HeaderView = findViewById(R.id.header)!!
    private val contentView: View = findViewById(R.id.content)!!
    private val iconView: AsyncImageView = findViewById(R.id.icon)!!
    private val titleView: AppCompatTextView = findViewById(R.id.title)!!
    private val subtitleView: AppCompatTextView = findViewById(R.id.subtitle)!!
    private val infoView: AppCompatTextView = findViewById(R.id.info)!!
    private val button: Button = findViewById(R.id.button)!!
    private val checkboxContainer: View = findViewById(R.id.checkbox_container)!!
    private val checkbox: CheckBoxView = findViewById(R.id.checkbox)!!

    init {
        infoView.movementMethod = LinkMovementMethod.getInstance()
        headerView.doOnActionClick = { dismiss() }
        checkboxContainer.setOnClickListener { checkbox.toggle() }
        contentView.applyNavBottomPadding()
        setCancelable(false)
    }

    fun show(
        method: PurchaseMethodEntity,
        callback: (showAgain: Boolean) -> Unit
    ) {
        super.show()
        iconView.setImageURI(method.iconUrl, this)
        titleView.text = method.title
        subtitleView.text = method.subtitle
        button.text = method.actionButton.title
        button.setOnClickListener {
            callback(!checkbox.checked)
            dismiss()
        }
        checkbox.checked = false
        // applyInfoButtons(method.infoButtons)
    }

    fun show(
        provider: ProviderEntity,
        callback: (showAgain: Boolean) -> Unit
    ) {
        super.show()
        iconView.setImageURI(provider.iconUrl, this)
        titleView.text = provider.title
        subtitleView.text = provider.description
        button.text = context.getString(Localization.open)
        button.setOnClickListener {
            callback(!checkbox.checked)
            dismiss()
        }
        checkbox.checked = false
        applyInfoButtons(provider.buttons)
    }

    private fun applyInfoButtons(buttons: List<MerchantEntity.Button>) {
        if (buttons.isNotEmpty()) {
            val builder = SpannableStringBuilder()
            for ((index, button) in buttons.withIndex()) {
                builder.clickable(
                    color = context.accentBlueColor,
                    onClick = { openBrowser(button.url) },
                    builderAction = { append(button.title) }
                )
                val isLast = index == buttons.size - 1
                if (!isLast) {
                    builder.append(" · ")
                }
            }
            infoView.text = builder
        } else {
            infoView.text = context.getString(Localization.provider)
        }
    }

    private fun openBrowser(url: String) {
        BrowserHelper.open(context, url)
        dismiss()
    }
}