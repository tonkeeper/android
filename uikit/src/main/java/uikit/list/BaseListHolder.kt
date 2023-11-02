package uikit.list

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
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

    fun <V : View> findViewById(id: Int): V = itemView.findViewById<V>(id)

    @CallSuper
    open fun bind(item: BaseListItem) {
        onBind(item as I)
    }

    abstract fun onBind(item: I)

    fun getString(resId: Int): String = context.getString(resId)
}
