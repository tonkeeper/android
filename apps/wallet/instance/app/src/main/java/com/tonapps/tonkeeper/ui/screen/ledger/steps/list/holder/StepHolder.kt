package com.tonapps.tonkeeper.ui.screen.ledger.steps.list.holder

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.tonapps.tonkeeper.ui.screen.ledger.steps.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.UIKitColor
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.textPrimaryColor
import uikit.widget.LoaderView

class StepHolder(
    parent: ViewGroup,
): Holder<Item.Step>(parent, R.layout.fragment_legder_step_item) {

    private val labelView = findViewById<TextView>(R.id.step_label)
    private val loaderView = findViewById<LoaderView>(R.id.loader)
    private val dotView = findViewById<AppCompatImageView>(R.id.dot_icon)
    private val doneView = findViewById<AppCompatImageView>(R.id.done_icon)

    override fun onBind(item: Item.Step) {
        labelView.text = item.label
        labelView.setTextColor(if (item.isDone) context.accentGreenColor else context.textPrimaryColor)
        loaderView.visibility = if (item.isCurrent) View.VISIBLE else View.GONE
        dotView.visibility = if (!item.isCurrent && !item.isDone) View.VISIBLE else View.GONE
        doneView.visibility = if (item.isDone) View.VISIBLE else View.GONE
    }
}