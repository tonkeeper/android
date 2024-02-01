package uikit.list

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import uikit.extensions.inflate
import uikit.navigation.Navigation

abstract class BaseListHolder<I: BaseListItem>(
    view: View
): RecyclerView.ViewHolder(view) {

    constructor(
        parent: ViewGroup,
        @LayoutRes resId: Int
    ) : this(
        parent.inflate(resId),
    )

    val context: Context
        get() = itemView.context

    val nav: Navigation?
        get() = Navigation.from(context)

    var item: I? = null
        private set

    fun <V : View> findViewById(id: Int): V = itemView.findViewById<V>(id)

    @CallSuper
    open fun bind(item: BaseListItem) {
        this.item = item as I
        onBind(item)
    }

    abstract fun onBind(item: I)

    fun getString(resId: Int): String = context.getString(resId)

    @ColorInt
    fun getColor(resId: Int): Int = context.getColor(resId)
}
