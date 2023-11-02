package com.tonkeeper.dialog

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.drawee.view.SimpleDraweeView
import com.tonkeeper.R
import com.tonkeeper.api.shortAddress
import com.tonkeeper.api.userLikeAddress
import uikit.base.BaseSheetDialog
import uikit.widget.LoaderView

class TonConnectDialog(context: Context): BaseSheetDialog(context) {

    private val loaderView: LoaderView
    private val contentView: View
    private val appIconView: SimpleDraweeView
    private val siteIconView: SimpleDraweeView
    private val nameView: AppCompatTextView
    private val descriptionView: AppCompatTextView

    init {
        setContentView(R.layout.dialog_ton_connect)
        loaderView = findViewById(R.id.loader)!!
        contentView = findViewById(R.id.content)!!
        appIconView = findViewById(R.id.app_icon)!!
        appIconView.setImageURI("res:///${R.raw.tonkeeper_logo}")
        siteIconView = findViewById(R.id.site_icon)!!
        nameView = findViewById(R.id.name)!!
        descriptionView = findViewById(R.id.description)!!
    }

    override fun show() {
        super.show()
        loaderView.visibility = View.VISIBLE
        loaderView.resetAnimation()

        contentView.visibility = View.GONE
    }

    fun setData(
        iconUrl: String,
        name: String,
        url: String,
        address: String,
    ) {
        siteIconView.setImageURI(iconUrl)
        nameView.text = context.getString(R.string.ton_connect_title, name)
        descriptionView.text = context.getString(R.string.ton_connect_description, Uri.parse(url).host, address.shortAddress, "V")

        showContent()
    }

    private fun showContent() {
        loaderView.visibility = View.GONE
        loaderView.stopAnimation()

        contentView.visibility = View.VISIBLE
    }

}