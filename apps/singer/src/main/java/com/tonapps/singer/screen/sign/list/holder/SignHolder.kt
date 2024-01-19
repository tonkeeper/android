package com.tonapps.singer.screen.sign.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.singer.R
import com.tonapps.singer.screen.sign.list.SignItem
import uikit.list.BaseListHolder

abstract class SignHolder<I: SignItem>(
    parent: ViewGroup
): BaseListHolder<I>(parent, R.layout.view_message_action) {

    val iconView = findViewById<AppCompatImageView>(R.id.icon)
    val titleView = findViewById<AppCompatTextView>(R.id.title)
    val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)
    val amountView = findViewById<AppCompatTextView>(R.id.amount)
    val amount2View = findViewById<AppCompatTextView>(R.id.amount2)
    val dateView = findViewById<AppCompatTextView>(R.id.date)
    val bodyView = findViewById<View>(R.id.body)
    val commentView = findViewById<AppCompatTextView>(R.id.comment)
}