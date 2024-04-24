package com.tonapps.tonkeeper.core.history.list.holder

import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.ListCell
import uikit.extensions.drawable
import uikit.navigation.Navigation
import uikit.widget.FrescoView

class HistoryAppHolder(
    parent: ViewGroup
): HistoryHolder<HistoryItem.App>(parent, R.layout.view_history_app) {

    private val imageView = itemView.findViewById<FrescoView>(R.id.image)
    private val messageView = itemView.findViewById<AppCompatTextView>(R.id.message)
    private val dataView = itemView.findViewById<AppCompatTextView>(R.id.data)

    init {
        itemView.background = ListCell.Position.SINGLE.drawable(context)
    }

    override fun onBind(item: HistoryItem.App) {
        itemView.setOnClickListener {
            Navigation.from(context)?.add(
                DAppScreen.newInstance(
                item.title,
                item.host,
                item.deepLink
            ))
        }
        imageView.setImageURI(item.iconUri, this)
        messageView.text = item.body
        dataView.text = createData(item.title, item.date)
    }

    private fun createData(title: String, date: String): String {
        val builder = StringBuilder()
        builder.append(title)
        builder.append(" · ")
        builder.append(date)
        return builder.toString()
    }

}