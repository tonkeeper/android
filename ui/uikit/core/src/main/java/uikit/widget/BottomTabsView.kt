package uikit.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.WindowInsetsCompat
import com.tonapps.uikit.color.backgroundTransparentColor
import com.tonapps.uikit.color.tabBarActiveIconColor
import com.tonapps.uikit.color.tabBarInactiveIconColor
import uikit.R
import uikit.drawable.FooterDrawable
import uikit.extensions.createRipple
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingBottom
import uikit.extensions.setPaddingHorizontal
import uikit.extensions.useAttributes

@SuppressLint("RestrictedApi")
class BottomTabsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RowLayout(context, attrs, defStyle) {

    private val barHeight = context.getDimensionPixelSize(R.dimen.barHeight)
    private var bottomOffset: Int = 0
        set(value) {
            if (field != value) {
                field = value
                setPaddingBottom(value)
                requestLayout()
            }
        }

    private val drawable = FooterDrawable(context).apply {
        setColor(context.backgroundTransparentColor)
    }

    private val menu: MenuBuilder by lazy { MenuBuilder(context) }
    private val menuInflater: MenuInflater by lazy { MenuInflater(context) }
    private var selectedItemId = 0
        set(value) {
            if (field != value) {
                field = value
                updateSelected()
            }
        }

    var doOnClick: ((itemId: Int) -> Unit)? = null
    var doOnLongClick: ((itemId: Int) -> Unit)? = null

    init {
        background = drawable
        setPaddingHorizontal(context.getDimensionPixelSize(R.dimen.offsetMedium))
        context.useAttributes(attrs, R.styleable.BottomTabsView) {
            if (it.hasValue(R.styleable.BottomTabsView_menu)) {
                inflateMenu(it.getResourceId(R.styleable.BottomTabsView_menu, 0))
            }
        }
    }

    fun setDivider(value: Boolean) {
        drawable.setDivider(value)
    }

    fun hideItem(id: Int) {
        for (i in 0 until childCount) {
            val view = getChildAt(i)
            if (view.tag == id) {
                view.visibility = View.GONE
            }
        }
    }

    fun showItem(id: Int) {
        for (i in 0 until childCount) {
            val view = getChildAt(i)
            if (view.tag == id) {
                view.visibility = View.VISIBLE
            }
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val navigationInsets = compatInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
        bottomOffset = navigationInsets.bottom
        return super.onApplyWindowInsets(insets)
    }

    fun enableDot(itemId: Int, enable: Boolean) {
        for (i in 0 until childCount) {
            val view = getChildAt(i)
            if (view.tag == itemId) {
                val dotView = view.findViewById<View>(R.id.dot)
                dotView.visibility = if (enable) View.VISIBLE else View.GONE
            }
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
                selectedItemId = item.itemId
            }
            addMenu(item)
        }

        updateSelected()
    }

    private fun updateSelected() {
        val colorActive = context.tabBarActiveIconColor
        val colorInactive = context.tabBarInactiveIconColor

        for (i in 0 until childCount) {
            val view = getChildAt(i)
            val iconView = view.findViewById<ImageView>(R.id.icon)
            val titleView = view.findViewById<TextView>(R.id.title)
            if (view.tag == selectedItemId) {
                iconView.imageTintList = ColorStateList.valueOf(colorActive)
                titleView.setTextColor(colorActive)
            } else {
                iconView.imageTintList = ColorStateList.valueOf(colorInactive)
                titleView.setTextColor(colorInactive)
            }
        }
    }

    private fun addMenu(menuItem: MenuItem) {
        val view = inflate(context, R.layout.view_bottom_tab, null)
        view.tag = menuItem.itemId
        view.background = context.createRipple()
        view.visibility = if (menuItem.isVisible) {
            View.VISIBLE
        } else {
            View.GONE
        }
        val iconView = view.findViewById<ImageView>(R.id.icon)
        val titleView = view.findViewById<TextView>(R.id.title)
        iconView.setImageDrawable(menuItem.icon!!)
        titleView.text = menuItem.title!!

        if (menuItem.isCheckable) {
            view.setOnClickListener {
                selectedItemId = menuItem.itemId
                doOnClick?.invoke(menuItem.itemId)
            }
            view.setOnLongClickListener {
                doOnLongClick?.invoke(menuItem.itemId)
                true
            }
        }

        addView(view, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    fun setItemChecked(itemId: Int) {
        selectedItemId = itemId
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(barHeight + bottomOffset, MeasureSpec.EXACTLY))
    }
}