package uikit.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RawRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.WindowInsetsCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.tonapps.uikit.color.tabBarActiveIconColor
import com.tonapps.uikit.color.tabBarInactiveIconColor
import uikit.R
import uikit.drawable.FooterDrawable
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingBottom
import uikit.extensions.setPaddingHorizontal
import uikit.extensions.useAttributes
import uikit.extensions.setMoonOnClickListener

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

    private val drawable = FooterDrawable(context)

    private val menu: MenuBuilder by lazy { MenuBuilder(context) }
    private val menuInflater: MenuInflater by lazy { MenuInflater(context) }

    var selectedItemId = 0
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

    fun setLottieTabIcons(@RawRes rawResByItemId: Map<Int, Int>?) {
        if (rawResByItemId.isNullOrEmpty()) {
            return
        }
        for (i in 0 until childCount) {
            val tab = getChildAt(i)
            val itemId = tab.tag as? Int ?: continue
            val raw = rawResByItemId[itemId] ?: continue
            installLottieIcon(tab, raw)
        }
        updateSelected()
    }

    private fun installLottieIcon(tab: View, @RawRes rawRes: Int) {
        val iconView = tab.findViewById<ImageView>(R.id.icon) ?: return
        if (iconView is LottieAnimationView) {
            return
        }
        val parent = iconView.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(iconView)
        val lp = iconView.layoutParams
        parent.removeViewAt(index)
        val lottie = LottieAnimationView(context).apply {
            id = R.id.icon
            layoutParams = lp
            setAnimation(rawRes)
            repeatCount = 0
            repeatMode = LottieDrawable.RESTART
            progress = 0f
            pauseAnimation()
        }
        parent.addView(lottie, index)
    }

    private fun applyLottieTabIconTint(lottie: LottieAnimationView, color: Int) {
        lottie.clearValueCallback(KeyPath("**"), LottieProperty.COLOR_FILTER)
        lottie.addValueCallback(KeyPath("**"), LottieProperty.COLOR_FILTER) {
            PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
        lottie.imageTintList = null
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

    fun setBgColor(color: Int) {
        drawable.setColor(color)
    }

    fun showItem(id: Int) {
        for (i in 0 until childCount) {
            val view = getChildAt(i)
            if (view.tag == id) {
                view.visibility = View.VISIBLE
            }
        }
    }

    fun toggleItem(id: Int, show: Boolean) {
        if (show) {
            showItem(id)
        } else {
            hideItem(id)
        }
    }

    fun findTabView(itemId: Int): View? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.tag == itemId) return child
        }
        return null
    }

    fun indexOfItem(itemId: Int): Int {
        for (i in 0 until childCount) {
            if (getChildAt(i).tag == itemId) {
                return i
            }
        }
        return -1
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
            val iconView = view.findViewById<ImageView>(R.id.icon) ?: continue
            val titleView = view.findViewById<TextView>(R.id.title) ?: continue
            val isSelected = view.tag == selectedItemId
            if (!isSelected && iconView is LottieAnimationView) {
                iconView.cancelAnimation()
                iconView.progress = 0f
            }
            val color = if (isSelected) colorActive else colorInactive
            if (iconView is LottieAnimationView) {
                applyLottieTabIconTint(iconView, color)
            } else {
                iconView.colorFilter = null
                iconView.imageTintList = ColorStateList.valueOf(color)
            }
            titleView.setTextColor(color)
        }
    }

    private fun addMenu(menuItem: MenuItem) {
        val view = inflate(context, R.layout.view_bottom_tab, null)
        view.tag = menuItem.itemId
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
            view.setMoonOnClickListener {
                val icon = view.findViewById<ImageView>(R.id.icon)
                if (icon is LottieAnimationView) {
                    icon.cancelAnimation()
                    icon.progress = 0f
                    icon.playAnimation()
                }
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
