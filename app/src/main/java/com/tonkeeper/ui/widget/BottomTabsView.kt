package com.tonkeeper.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.LinearLayoutCompat
import com.tonkeeper.R

@SuppressLint("RestrictedApi")
class BottomTabsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    private val menu: MenuBuilder by lazy { MenuBuilder(context) }
    private val menuInflater: MenuInflater by lazy { MenuInflater(context) }
    private var selectedIndex = 0

    var doOnClick: ((index: Int, itemId: Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL

        context.theme.obtainStyledAttributes(attrs, R.styleable.BottomTabsView,0, 0).apply {
            try {
                initAttrs(this)
            } finally {
                recycle()
            }
        }
    }

    private fun initAttrs(attrs: TypedArray) {
        if (attrs.hasValue(R.styleable.BottomTabsView_menu)) {
            inflateMenu(attrs.getResourceId(R.styleable.BottomTabsView_menu, 0))
        }
    }

    private fun inflateMenu(resId: Int) {
        menuInflater.inflate(resId, menu)
        if (menu.size() > 0) {
            initMenu()
        }
    }

    private fun initMenu() {
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.isChecked) {
                selectedIndex = i
            }
            addMenu(i, item)
        }

        updateSelected()
    }

    private fun updateSelected() {
        val colorAccent = context.getColor(R.color.accent)
        val colorSecondary = context.getColor(R.color.secondary)

        for (i in 0 until childCount) {
            val view = getChildAt(i)
            val iconView = view.findViewById<ImageView>(R.id.iv_tab_icon)
            val titleView = view.findViewById<TextView>(R.id.tv_tab_title)
            if (view.tag == selectedIndex) {
                iconView.imageTintList = ColorStateList.valueOf(colorAccent)
                titleView.setTextColor(colorAccent)
            } else {
                iconView.imageTintList = ColorStateList.valueOf(colorSecondary)
                titleView.setTextColor(colorSecondary)
            }
        }
    }

    private fun addMenu(index: Int, menuItem: MenuItem) {
        val view = inflate(context, R.layout.view_bottom_tab, null)
        view.tag = index
        val iconView = view.findViewById<ImageView>(R.id.iv_tab_icon)
        val titleView = view.findViewById<TextView>(R.id.tv_tab_title)
        iconView.setImageDrawable(menuItem.icon!!)
        titleView.text = menuItem.title!!

        if (menuItem.isCheckable) {
            view.setOnClickListener {
                selectedIndex = index
                doOnClick?.invoke(index, menuItem.itemId)
                updateSelected()
            }
        }

        addView(view, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = context.resources.getDimension(R.dimen.view_bar_height)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height.toInt(), MeasureSpec.EXACTLY))
    }
}