package com.tonapps.tonkeeper.ui.screen.purchase.main

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import com.facebook.drawee.view.SimpleDraweeView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import uikit.dialog.modal.ModalDialog
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.dp
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.CheckBoxView
import uikit.widget.HeaderView

class PurchaseConfirmDialog(
    context: Context
): ModalDialog(context, R.layout.fragment_purchase_confirm) {

    private val headerView: HeaderView = findViewById(R.id.header)!!
    private val contentView: View = findViewById(R.id.content)!!
    private val iconView: SimpleDraweeView = findViewById(R.id.icon)!!
    private val titleView: AppCompatTextView = findViewById(R.id.title)!!
    private val subtitleView: AppCompatTextView = findViewById(R.id.subtitle)!!
    private val infoView: LinearLayoutCompat = findViewById(R.id.info)!!
    private val button: Button = findViewById(R.id.button)!!
    private val checkboxContainer: View = findViewById(R.id.checkbox_container)!!
    private val checkbox: CheckBoxView = findViewById(R.id.checkbox)!!

    init {
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
        iconView.setImageURI(method.iconUrl)
        titleView.text = method.title
        subtitleView.text = method.subtitle
        button.text = method.actionButton.title
        button.setOnClickListener {
            callback(!checkbox.checked)
            dismiss()
        }
        checkbox.checked = false
        applyInfoButtons(method.infoButtons)
    }

    private fun applyInfoButtons(buttons: List<PurchaseMethodEntity.Button>) {
        if (buttons.isNotEmpty()) {
            infoView.removeAllViews()
            infoView.visibility = View.VISIBLE
            for (button in buttons) {
                infoView.addView(createInfoButton(button))
                infoView.addView(View(context), ViewGroup.LayoutParams(6.dp, 0))
            }
        } else {
            infoView.visibility = View.GONE
        }
    }

    private fun createInfoButton(button: PurchaseMethodEntity.Button): View {
        val buttonView = AppCompatTextView(context)
        buttonView.text = button.title
        buttonView.setOnClickListener {
            navigation?.openURL(button.url, true)
        }
        buttonView.setTextAppearance(uikit.R.style.TextAppearance_Body1)
        buttonView.setTextColor(context.textSecondaryColor)
        return buttonView
    }
}