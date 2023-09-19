package com.tonkeeper.uikit.list

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.uikit.extensions.inflate

abstract class BaseListHolder<I: BaseListItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): RecyclerView.ViewHolder(parent.inflate(resId)) {

    val context: Context
        get() = itemView.context

    fun <V : View> findViewById(id: Int): V = itemView.findViewById<V>(id)

    @CallSuper
    open fun bind(item: BaseListItem) {
        onBind(item as I)
    }

    abstract fun onBind(item: I)
}
