package com.tonapps.tonkeeper.ui.screen.wallet.picker.list

import android.content.Context
import android.text.Layout
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.uikit.color.textPrimaryColor
import uikit.extensions.dp
import uikit.extensions.setPaddingHorizontal
import uikit.widget.TextHeaderView

class TitleAdapter(
    private val title: String,
    private val subtitle: String,
): RecyclerView.Adapter<TitleAdapter.Holder>() {

    class Holder(view: View): RecyclerView.ViewHolder(view)

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(createView(parent.context))

    override fun onBindViewHolder(holder: Holder, position: Int) = Unit

    fun measureHeight(context: Context, width: Int): Int {
        val view = createView(context)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return view.measuredHeight
    }

    private fun createView(context: Context): TextHeaderView {
        val view = TextHeaderView(context)
        view.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        view.setPaddingHorizontal(24.dp)
        view.updatePadding(bottom = 16.dp)
        view.titleView.setTextAppearance(uikit.R.style.TextAppearance_H3)
        view.titleView.setTextColor(context.textPrimaryColor)
        view.descriptionView.breakStrategy = Layout.BREAK_STRATEGY_BALANCED
        view.title = title
        view.desciption = subtitle
        return view
    }
}
