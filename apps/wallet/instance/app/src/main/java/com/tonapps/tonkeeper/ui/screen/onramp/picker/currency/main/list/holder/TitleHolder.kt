package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list.holder

import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.dp
import uikit.extensions.hideKeyboard
import uikit.widget.FrescoView
import uikit.widget.RowLayout

class TitleHolder(parent: ViewGroup): Holder<Item.Title>(parent, R.layout.view_purchase_title) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)

    init {
        itemView.setOnClickListener {
            context.hideKeyboard()
        }
    }

    override fun onBind(item: Item.Title) {
        titleView.text = item.title
        applyIcons(item.icons)
        if (item.withoutMargin) {
            itemView.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, 32.dp).apply {
                bottomMargin = 10.dp
            }
        } else {
            itemView.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, 48.dp).apply {
                topMargin = 18.dp
            }
        }
    }

    private fun applyIcons(icons: List<Uri>) {
        val view = itemView as RowLayout
        if (view.childCount > 1) {
            view.removeViews(1, view.childCount - 1)
        }
        if (icons.isNotEmpty()) {
            view.addView(View(context), LinearLayoutCompat.LayoutParams(8.dp, 8.dp))
            val size = Size(24.dp, 16.dp)
            val views = icons.map { createIconView(it) }
            for (iconView in views) {
                view.addView(iconView, LinearLayoutCompat.LayoutParams(size.width, size.height).apply {
                    gravity = Gravity.CENTER
                    marginEnd = 1.dp
                    topMargin = 2.dp
                })
            }
        }
    }

    private fun createIconView(uri: Uri): FrescoView {
        val view = FrescoView(context)
        view.setRound(3f.dp)
        view.setImageURI(uri, null)
        return view
    }
}